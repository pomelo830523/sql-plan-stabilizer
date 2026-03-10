package com.example.enhancesql.repository;

import com.example.enhancesql.model.CaseInfoPK;
import com.example.enhancesql.model.QueryResponse.CaseResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class CaseRepository {

    // ── 常數 ──────────────────────────────────────────────────
    private static final int[]  BUCKET_SIZES = {1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1000, 2000};
    private static final int    BATCH_SIZE   = 100;
    private static final String DUMMY_LOT    = "_DUMMY_LOT_";
    private static final String DUMMY_CASE   = "_DUMMY_CASE_";
    private static final String DUMMY_WAFER  = "_DUMMY_WAFER_";

    private final JdbcTemplate jdbc;

    /**
     * Oracle hint，可在 application.properties 設定：
     *   query.hint=LEADING(S L Y) USE_NL(L) USE_NL(Y) INDEX(S) INDEX(Y)
     *
     * 留空代表不加 hint，讓 CBO 自行決定。
     * 建議先用 DBMS_XPLAN 確認執行計畫後再填入。
     */
    @Value("${query.hint:}")
    private String queryHint;

    public CaseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 供 Controller 回傳目前使用的 hint 給前端顯示 */
    public String getActiveHint() {
        return queryHint == null ? "" : queryHint.trim();
    }

    // ════════════════════════════════════════════════════════
    // 方法一 Original：placeholder 數量 = list 大小（每次不同）
    // ════════════════════════════════════════════════════════
    public List<CaseResult> queryOriginal(List<CaseInfoPK> keys, int sortNo) {
        return executeMainQuery(buildMainSql(keys.size()), buildMainParams(keys, keys.size(), sortNo));
    }

    // ════════════════════════════════════════════════════════
    // 方法二 Fixed Bucket：placeholder 數量對齊固定 bucket
    // ════════════════════════════════════════════════════════
    public List<CaseResult> queryFixed(List<CaseInfoPK> keys, int sortNo) {
        int bucket = getBucketSize(keys.size());
        return executeMainQuery(buildMainSql(bucket), buildMainParams(keys, bucket, sortNo));
    }

    // ════════════════════════════════════════════════════════
    // 方法三 Plan A — Fixed Batch：
    //   每批固定 BATCH_SIZE 筆，多批串接結果
    //   ✓ SQL 文字永遠固定 → plan 100% 穩定，無 bucket 上限
    // ════════════════════════════════════════════════════════
    public BatchQueryResult queryBatch(List<CaseInfoPK> keys, int sortNo) {
        List<CaseResult> allResults = new ArrayList<>();
        int batchCount = 0;

        for (int i = 0; i < keys.size(); i += BATCH_SIZE) {
            List<CaseInfoPK> batch = keys.subList(i, Math.min(i + BATCH_SIZE, keys.size()));
            allResults.addAll(
                executeMainQuery(buildMainSql(BATCH_SIZE), buildMainParams(batch, BATCH_SIZE, sortNo))
            );
            batchCount++;
        }
        return new BatchQueryResult(allResults, batchCount, batchCount);
    }

    // ════════════════════════════════════════════════════════
    // 方法四 Plan A+C — Fixed Batch + Pre-compute WAFER_ID：
    //   Phase 1：查 WAFER_SUM + LOT_WIP（走 (LOT_ID, CASE_ID) index）
    //   Java：預先計算 CP_WFRYLD 的 WAFER_ID，完全避免 function-based JOIN
    //   Phase 2：查 CP_WFRYLD（走 WAFER_ID index）
    //   Java：join + SUM 聚合
    //   ✓ 兩張大表都走 index，幾千萬筆不再怕
    // ════════════════════════════════════════════════════════
    public BatchQueryResult queryTwoPhase(List<CaseInfoPK> keys, int sortNo) {
        List<CaseResult> allResults = new ArrayList<>();
        int batchCount = 0;
        int roundTrips = 0;

        for (int i = 0; i < keys.size(); i += BATCH_SIZE) {
            List<CaseInfoPK> batch = keys.subList(i, Math.min(i + BATCH_SIZE, keys.size()));
            BatchQueryResult batchResult = executeTwoPhase(batch, sortNo);
            allResults.addAll(batchResult.results);
            roundTrips += batchResult.dbRoundTrips;
            batchCount++;
        }
        return new BatchQueryResult(allResults, batchCount, roundTrips);
    }

    // ── 公開工具方法 ──────────────────────────────────────────
    public int getBucketSize(int size) {
        for (int b : BUCKET_SIZES) { if (size <= b) return b; }
        return size;
    }

    public static int getBatchSize() { return BATCH_SIZE; }

    // ════════════════════════════════════════════════════════
    // Plan A+C 內部：單一批次的兩階段執行
    // ════════════════════════════════════════════════════════
    private BatchQueryResult executeTwoPhase(List<CaseInfoPK> batch, int sortNo) {

        // ── Phase 1：查 WAFER_SUM × LOT_WIP（不碰 CP_WFRYLD）──
        //   優點：(LOT_ID, CASE_ID) IN (...) 可直接走 PK / composite index
        //   CP_WFRYLD 的 function-based JOIN 在此完全移除
        int slots1 = BATCH_SIZE;
        String sql1 = buildPhase1Sql(slots1);
        Object[] params1 = buildPhase1Params(batch, slots1);

        List<WaferSumRow> phase1Rows = jdbc.query(sql1, params1, (rs, n) -> new WaferSumRow(
            rs.getString("LOT_ID"),
            rs.getString("CASE_ID"),
            rs.getString("WAFER_ID"),
            rs.getString("TYPE_FLAG"),
            rs.getTimestamp("INSP_DT")
        ));

        if (phase1Rows.isEmpty()) return new BatchQueryResult(Collections.emptyList(), 1, 1);

        // ── Java：預先計算 CP_WFRYLD 的 WAFER_ID ──
        //   原 SQL：SUBSTR(L.LOT_ID, 1, INSTR(L.LOT_ID,'.')) || SUBSTR(S.WAFER_ID, INSTR(S.WAFER_ID,'.')+1, 2)
        //   以 Java 字串操作等效實現，避免 DB 內的 function 計算
        List<String> cpWaferIds = phase1Rows.stream()
            .map(r -> computeCpWaferId(r.lotId, r.waferId))
            .distinct()
            .collect(Collectors.toList());

        // ── Phase 2：查 CP_WFRYLD（WAFER_ID 可走 index）──
        int slots2 = getBucketSize(cpWaferIds.size());
        String sql2 = buildPhase2Sql(slots2);
        Object[] params2 = buildPhase2Params(cpWaferIds, slots2, sortNo);

        Map<String, Timestamp> cpMap = new HashMap<>();
        jdbc.query(sql2, params2, (rs) -> {
            // key = "WAFER_ID|TYPE_FLAG" 確保精確配對
            String key = rs.getString("WAFER_ID") + "|" + rs.getString("TYPE_FLAG");
            cpMap.put(key, rs.getTimestamp("END_TEST_TIME"));
        });

        // ── Java JOIN + SUM 聚合 ──
        Map<String, double[]> aggMap = new LinkedHashMap<>();
        Map<String, String[]> keyMap = new LinkedHashMap<>();

        for (WaferSumRow row : phase1Rows) {
            if (row.inspDt == null) continue;
            String cpWaferId = computeCpWaferId(row.lotId, row.waferId);
            Timestamp endTestTime = cpMap.get(cpWaferId + "|" + row.typeFlag);
            if (endTestTime == null) continue;

            double diffSec = (row.inspDt.getTime() - endTestTime.getTime()) / 1000.0;
            String aggKey = row.lotId + "|" + row.caseId;
            aggMap.merge(aggKey, new double[]{diffSec}, (a, b) -> { a[0] += b[0]; return a; });
            keyMap.putIfAbsent(aggKey, new String[]{row.lotId, row.caseId});
        }

        List<CaseResult> results = aggMap.entrySet().stream()
            .map(e -> {
                String[] k = keyMap.get(e.getKey());
                return new CaseResult(k[0], k[1], e.getValue()[0]);
            })
            .collect(Collectors.toList());

        return new BatchQueryResult(results, 1, 2); // 本批 2 次 round trip
    }

    // ════════════════════════════════════════════════════════
    // SQL 建構
    // ════════════════════════════════════════════════════════

    /** 原始三表 JOIN（方法一、二、三共用） */
    private String buildMainSql(int slots) {
        StringBuilder sb = new StringBuilder();
        // hint 非空時插入 /*+ ... */，方便對照有無 hint 的執行計畫差異
        if (getActiveHint().isEmpty()) {
            sb.append("SELECT S.LOT_ID, S.CASE_ID, ");
        } else {
            sb.append("SELECT /*+ ").append(getActiveHint()).append(" */ S.LOT_ID, S.CASE_ID, ");
        }
        sb.append("  SUM(DATEDIFF('SECOND', Y.END_TEST_TIME, ");
        sb.append("      COALESCE(S.ALL_INSP_DT, S.SAMPLE_INSP_DT))) AS TOTAL_TIME_DIFF ");
        sb.append("FROM WAFER_SUM S ");
        sb.append("JOIN LOT_WIP L ON L.LOT_ID = S.LOT_ID ");
        sb.append("JOIN CP_WFRYLD Y ");
        // ★ 此處 function-based JOIN 是幾千萬筆下的效能瓶頸
        sb.append("  ON Y.WAFER_ID = SUBSTRING(L.LOT_ID, 1, LOCATE('.', L.LOT_ID)) || ");
        sb.append("     SUBSTRING(S.WAFER_ID, LOCATE('.', S.WAFER_ID) + 1, 2) ");
        sb.append("  AND Y.TYPE_FLAG = L.TYPE_FLAG ");
        sb.append("WHERE (S.LOT_ID, S.CASE_ID) IN (");
        for (int i = 0; i < slots; i++) { if (i > 0) sb.append(","); sb.append("(?,?)"); }
        sb.append(") AND Y.SORT_NO = ? AND S.WAIVE_INSP_FLAG <> 'Y' ");
        sb.append("GROUP BY S.LOT_ID, S.CASE_ID");
        return sb.toString();
    }

    /** Phase 1：只查 WAFER_SUM × LOT_WIP，不碰 CP_WFRYLD */
    private String buildPhase1Sql(int slots) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT S.LOT_ID, S.CASE_ID, S.WAFER_ID, L.TYPE_FLAG, ");
        sb.append("  COALESCE(S.ALL_INSP_DT, S.SAMPLE_INSP_DT) AS INSP_DT ");
        sb.append("FROM WAFER_SUM S ");
        sb.append("JOIN LOT_WIP L ON L.LOT_ID = S.LOT_ID ");
        sb.append("WHERE (S.LOT_ID, S.CASE_ID) IN (");
        for (int i = 0; i < slots; i++) { if (i > 0) sb.append(","); sb.append("(?,?)"); }
        sb.append(") AND S.WAIVE_INSP_FLAG <> 'Y'");
        return sb.toString();
    }

    /** Phase 2：用預先計算好的 WAFER_ID 直接查 CP_WFRYLD（可走 index） */
    private String buildPhase2Sql(int slots) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT WAFER_ID, TYPE_FLAG, END_TEST_TIME ");
        sb.append("FROM CP_WFRYLD ");
        sb.append("WHERE WAFER_ID IN (");
        for (int i = 0; i < slots; i++) { if (i > 0) sb.append(","); sb.append("?"); }
        sb.append(") AND SORT_NO = ?");
        return sb.toString();
    }

    // ════════════════════════════════════════════════════════
    // 參數建構
    // ════════════════════════════════════════════════════════

    private Object[] buildMainParams(List<CaseInfoPK> keys, int slots, int sortNo) {
        Object[] p = new Object[slots * 2 + 1];
        for (int i = 0; i < slots; i++) {
            p[i * 2]     = i < keys.size() ? keys.get(i).getLotId()  : DUMMY_LOT;
            p[i * 2 + 1] = i < keys.size() ? keys.get(i).getCaseId() : DUMMY_CASE;
        }
        p[slots * 2] = sortNo;
        return p;
    }

    private Object[] buildPhase1Params(List<CaseInfoPK> keys, int slots) {
        Object[] p = new Object[slots * 2];
        for (int i = 0; i < slots; i++) {
            p[i * 2]     = i < keys.size() ? keys.get(i).getLotId()  : DUMMY_LOT;
            p[i * 2 + 1] = i < keys.size() ? keys.get(i).getCaseId() : DUMMY_CASE;
        }
        return p;
    }

    private Object[] buildPhase2Params(List<String> waferIds, int slots, int sortNo) {
        Object[] p = new Object[slots + 1];
        for (int i = 0; i < slots; i++) {
            p[i] = i < waferIds.size() ? waferIds.get(i) : DUMMY_WAFER;
        }
        p[slots] = sortNo;
        return p;
    }

    // ════════════════════════════════════════════════════════
    // 工具
    // ════════════════════════════════════════════════════════

    /**
     * 在 Java 端重現 Oracle SQL 中的 WAFER_ID 計算邏輯：
     *   SUBSTR(LOT_ID, 1, INSTR(LOT_ID,'.')) || SUBSTR(WAFER_ID, INSTR(WAFER_ID,'.')+1, 2)
     *
     * 範例：LOT_ID="LOT0001.W01", WAFER_ID="W01.01" → "LOT0001.01"
     */
    private String computeCpWaferId(String lotId, String waferId) {
        String prefix = lotId.substring(0, lotId.indexOf('.') + 1);
        int dotPos = waferId.indexOf('.');
        String suffix = waferId.substring(dotPos + 1, dotPos + 3);
        return prefix + suffix;
    }

    private List<CaseResult> executeMainQuery(String sql, Object[] params) {
        return jdbc.query(sql, params, (rs, n) ->
            new CaseResult(rs.getString("LOT_ID"), rs.getString("CASE_ID"), rs.getDouble("TOTAL_TIME_DIFF"))
        );
    }

    // ════════════════════════════════════════════════════════
    // Inner classes
    // ════════════════════════════════════════════════════════

    private static class WaferSumRow {
        final String lotId, caseId, waferId, typeFlag;
        final Timestamp inspDt;
        WaferSumRow(String lotId, String caseId, String waferId, String typeFlag, Timestamp inspDt) {
            this.lotId = lotId; this.caseId = caseId; this.waferId = waferId;
            this.typeFlag = typeFlag; this.inspDt = inspDt;
        }
    }

    public static class BatchQueryResult {
        public final List<CaseResult> results;
        public final int batchCount;
        public final int dbRoundTrips;
        BatchQueryResult(List<CaseResult> results, int batchCount, int dbRoundTrips) {
            this.results = results; this.batchCount = batchCount; this.dbRoundTrips = dbRoundTrips;
        }
    }
}
