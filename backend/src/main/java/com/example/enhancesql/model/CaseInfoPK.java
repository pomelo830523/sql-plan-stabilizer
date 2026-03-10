package com.example.enhancesql.model;

import java.util.Objects;

public class CaseInfoPK {

    private String lotId;
    private String caseId;

    public CaseInfoPK() {}

    public CaseInfoPK(String lotId, String caseId) {
        this.lotId  = lotId;
        this.caseId = caseId;
    }

    public String getLotId()  { return lotId; }
    public String getCaseId() { return caseId; }
    public void setLotId(String lotId)   { this.lotId  = lotId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CaseInfoPK)) return false;
        CaseInfoPK that = (CaseInfoPK) o;
        return Objects.equals(lotId, that.lotId) && Objects.equals(caseId, that.caseId);
    }

    @Override public int hashCode() { return Objects.hash(lotId, caseId); }

    @Override public String toString() { return "(" + lotId + "," + caseId + ")"; }
}
