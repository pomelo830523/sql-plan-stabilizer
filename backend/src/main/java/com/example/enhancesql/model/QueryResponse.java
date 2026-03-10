package com.example.enhancesql.model;

import java.util.List;

public class QueryResponse {

    private List<CaseResult> results;
    private long executionTimeMs;
    private int inputSize;
    private int bucketSize;
    private int batchCount;    // Plan A / C：實際執行了幾批
    private int dbRoundTrips;  // 總 DB round trip 次數
    private String mode;
    private String note;

    public List<CaseResult> getResults()      { return results; }
    public long getExecutionTimeMs()          { return executionTimeMs; }
    public int getInputSize()                 { return inputSize; }
    public int getBucketSize()                { return bucketSize; }
    public int getBatchCount()                { return batchCount; }
    public int getDbRoundTrips()              { return dbRoundTrips; }
    public String getMode()                   { return mode; }
    public String getNote()                   { return note; }

    public void setResults(List<CaseResult> r)        { this.results = r; }
    public void setExecutionTimeMs(long v)            { this.executionTimeMs = v; }
    public void setInputSize(int v)                   { this.inputSize = v; }
    public void setBucketSize(int v)                  { this.bucketSize = v; }
    public void setBatchCount(int v)                  { this.batchCount = v; }
    public void setDbRoundTrips(int v)                { this.dbRoundTrips = v; }
    public void setMode(String v)                     { this.mode = v; }
    public void setNote(String v)                     { this.note = v; }

    public static class CaseResult {
        private final String lotId;
        private final String caseId;
        private final double totalTimeDiffSeconds;

        public CaseResult(String lotId, String caseId, double totalTimeDiffSeconds) {
            this.lotId = lotId;
            this.caseId = caseId;
            this.totalTimeDiffSeconds = totalTimeDiffSeconds;
        }

        public String getLotId()                { return lotId; }
        public String getCaseId()               { return caseId; }
        public double getTotalTimeDiffSeconds() { return totalTimeDiffSeconds; }
    }
}
