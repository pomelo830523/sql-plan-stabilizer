import { Component, Input } from '@angular/core';
import { CaseResult } from '../../services/case.service';

@Component({
  selector: 'app-result-table',
  template: `
    <table class="results-table">
      <thead>
        <tr>
          <th>Lot ID</th>
          <th>Case ID</th>
          <th>等待時間（秒）</th>
          <th>格式化</th>
        </tr>
      </thead>
      <tbody>
        <tr *ngIf="!results || results.length === 0">
          <td colspan="4" class="no-data">無符合資料（哨兵值過濾後為空是正常的）</td>
        </tr>
        <tr *ngFor="let r of results">
          <td>{{ r.lotId }}</td>
          <td>{{ r.caseId }}</td>
          <td>{{ r.totalTimeDiffSeconds | number:'1.0-0' }}</td>
          <td>{{ formatFn(r.totalTimeDiffSeconds) }}</td>
        </tr>
      </tbody>
    </table>
  `
})
export class ResultTableComponent {
  @Input() results: CaseResult[] = [];
  @Input() formatFn: (sec: number) => string = (s) => s + ' 秒';
}
