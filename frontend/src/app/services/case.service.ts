import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface CaseKey {
  lotId: string;
  caseId: string;
}

export interface QueryRequest {
  cases: CaseKey[];
  sortNo: number;
}

export interface CaseResult {
  lotId: string;
  caseId: string;
  totalTimeDiffSeconds: number;
}

export interface QueryResponse {
  mode: string;
  results: CaseResult[];
  executionTimeMs: number;
  inputSize: number;
  bucketSize: number;
  batchCount: number;
  dbRoundTrips: number;
  note: string;
}

@Injectable({ providedIn: 'root' })
export class CaseService {

  private base = environment.apiBase;

  constructor(private http: HttpClient) {}

  queryOriginal(req: QueryRequest):  Observable<QueryResponse> {
    return this.http.post<QueryResponse>(`${this.base}/query/original`, req);
  }
  queryFixed(req: QueryRequest):     Observable<QueryResponse> {
    return this.http.post<QueryResponse>(`${this.base}/query/fixed`, req);
  }
  queryBatch(req: QueryRequest):     Observable<QueryResponse> {
    return this.http.post<QueryResponse>(`${this.base}/query/batch`, req);
  }
  queryTwoPhase(req: QueryRequest):  Observable<QueryResponse> {
    return this.http.post<QueryResponse>(`${this.base}/query/two-phase`, req);
  }
}
