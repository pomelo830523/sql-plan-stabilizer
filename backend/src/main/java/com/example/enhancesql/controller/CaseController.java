package com.example.enhancesql.controller;

import com.example.enhancesql.model.QueryRequest;
import com.example.enhancesql.model.QueryResponse;
import com.example.enhancesql.model.QueryResponse.CaseResult;
import com.example.enhancesql.repository.CaseRepository;
import com.example.enhancesql.repository.CaseRepository.BatchQueryResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/query")
public class CaseController {

    private final CaseRepository repository;

    public CaseController(CaseRepository repository) {
        this.repository = repository;
    }

    /** 方法一：原始版本 — variable placeholder 數量 */
    @PostMapping("/original")
    public ResponseEntity<QueryResponse> queryOriginal(@RequestBody QueryRequest request) {
        if (isInvalid(request)) return ResponseEntity.badRequest().build();
        int n = request.getCases().size();
        long start = System.currentTimeMillis();
        List<CaseResult> results = repository.queryOriginal(request.getCases(), request.getSortNo());
        long ms = System.currentTimeMillis() - start;

        QueryResponse resp = buildResponse("original", results, ms, n, n, 0, 1,
            String.format("輸入 %d 筆 → SQL 有 %d 個 (?,?) placeholder。" +
                "每次大小改變，Oracle 視為新 SQL → hard-parse。", n, n));
        return ResponseEntity.ok(resp);
    }

    /** 方法二：Fixed Bucket — placeholder 對齊固定 bucket */
    @PostMapping("/fixed")
    public ResponseEntity<QueryResponse> queryFixed(@RequestBody QueryRequest request) {
        if (isInvalid(request)) return ResponseEntity.badRequest().build();
        int n = request.getCases().size();
        int bucket = repository.getBucketSize(n);
        long start = System.currentTimeMillis();
        List<CaseResult> results = repository.queryFixed(request.getCases(), request.getSortNo());
        long ms = System.currentTimeMillis() - start;

        QueryResponse resp = buildResponse("fixed", results, ms, n, bucket, 0, 1,
            String.format("輸入 %d 筆 → 對齊 bucket %d，%d 個 slot 填哨兵值。" +
                "SQL 文字固定，plan 穩定。但 list > 2000 時失效。", n, bucket, bucket - n));
        return ResponseEntity.ok(resp);
    }

    /** 方法三：Plan A — Fixed Batch，每批固定 100 筆 */
    @PostMapping("/batch")
    public ResponseEntity<QueryResponse> queryBatch(@RequestBody QueryRequest request) {
        if (isInvalid(request)) return ResponseEntity.badRequest().build();
        int n = request.getCases().size();
        long start = System.currentTimeMillis();
        BatchQueryResult r = repository.queryBatch(request.getCases(), request.getSortNo());
        long ms = System.currentTimeMillis() - start;

        QueryResponse resp = buildResponse("batch", r.results, ms, n, CaseRepository.getBatchSize(),
            r.batchCount, r.dbRoundTrips,
            String.format("輸入 %d 筆 → 切成 %d 批 × %d 筆，共 %d 次 DB round trip。" +
                "SQL 文字永遠固定（%d 個 placeholder），無上限。",
                n, r.batchCount, CaseRepository.getBatchSize(), r.dbRoundTrips, CaseRepository.getBatchSize()));
        return ResponseEntity.ok(resp);
    }

    /** 方法四：Plan A+C — Fixed Batch + Pre-compute WAFER_ID（不碰 function-based JOIN） */
    @PostMapping("/two-phase")
    public ResponseEntity<QueryResponse> queryTwoPhase(@RequestBody QueryRequest request) {
        if (isInvalid(request)) return ResponseEntity.badRequest().build();
        int n = request.getCases().size();
        long start = System.currentTimeMillis();
        BatchQueryResult r = repository.queryTwoPhase(request.getCases(), request.getSortNo());
        long ms = System.currentTimeMillis() - start;

        QueryResponse resp = buildResponse("two-phase", r.results, ms, n, CaseRepository.getBatchSize(),
            r.batchCount, r.dbRoundTrips,
            String.format("輸入 %d 筆 → %d 批，每批 2 次 round trip（共 %d 次）。" +
                "Phase 1 查 WAFER_SUM + LOT_WIP（走 index），" +
                "Java 預算 WAFER_ID，Phase 2 查 CP_WFRYLD（走 index）。" +
                "完全消除 function-based JOIN，幾千萬筆亦有效。",
                n, r.batchCount, r.dbRoundTrips));
        return ResponseEntity.ok(resp);
    }

    // ──────────────────────────────────────────────────────────

    private QueryResponse buildResponse(String mode, List<CaseResult> results, long ms,
                                        int inputSize, int bucketSize,
                                        int batchCount, int roundTrips, String note) {
        QueryResponse resp = new QueryResponse();
        resp.setMode(mode);
        resp.setResults(results);
        resp.setExecutionTimeMs(ms);
        resp.setInputSize(inputSize);
        resp.setBucketSize(bucketSize);
        resp.setBatchCount(batchCount);
        resp.setDbRoundTrips(roundTrips);
        resp.setNote(note);
        return resp;
    }

    private boolean isInvalid(QueryRequest req) {
        return req == null || req.getCases() == null || req.getCases().isEmpty();
    }
}
