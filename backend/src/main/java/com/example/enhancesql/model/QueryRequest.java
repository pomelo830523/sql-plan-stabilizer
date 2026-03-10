package com.example.enhancesql.model;

import java.util.List;

public class QueryRequest {

    private List<CaseInfoPK> cases;
    private int sortNo;

    public List<CaseInfoPK> getCases()  { return cases; }
    public int getSortNo()              { return sortNo; }
    public void setCases(List<CaseInfoPK> cases) { this.cases = cases; }
    public void setSortNo(int sortNo)            { this.sortNo = sortNo; }
}
