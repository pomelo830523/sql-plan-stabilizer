import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, FormArray, Validators } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { CaseService, QueryResponse } from '../../services/case.service';

export interface ResultSet {
  label: string;
  tagClass: string;
  noteClass: string;
  response: QueryResponse;
}

@Component({
  selector: 'app-query-panel',
  templateUrl: './query-panel.component.html',
  styleUrls: ['./query-panel.component.scss']
})
export class QueryPanelComponent implements OnInit {

  form!: FormGroup;
  results: ResultSet[] = [];
  loading = false;
  errorMsg = '';

  readonly TABLE_DISPLAY_LIMIT = 20;
  readonly PRESET_OPTIONS = [1, 5, 10, 50, 100, 300, 500, 1000, 5000, 10000];

  constructor(private fb: FormBuilder, private caseService: CaseService) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      sortNo: [1, [Validators.required, Validators.min(1)]],
      cases:  this.fb.array([])
    });
    this.addPresetRows(3);
  }

  get casesArray(): FormArray {
    return this.form.get('cases') as FormArray;
  }

  get visibleControls() {
    return this.casesArray.controls.slice(0, this.TABLE_DISPLAY_LIMIT);
  }

  addRow(lotId = '', caseId = ''): void {
    this.casesArray.push(this.fb.group({
      lotId:  [lotId,  Validators.required],
      caseId: [caseId, Validators.required]
    }));
  }

  removeRow(i: number): void {
    if (this.casesArray.length > 1) this.casesArray.removeAt(i);
  }

  addPresetRows(count: number): void {
    this.casesArray.clear();
    for (let i = 1; i <= count; i++) {
      const pad = String(i).padStart(5, '0');
      this.addRow(`LOT${pad}.W01`, `CASE_${pad}`);
    }
  }

  runAll(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.errorMsg = '';
    this.results = [];

    const req = {
      sortNo: this.form.value.sortNo,
      cases:  this.form.value.cases.map((c: any) => ({ lotId: c.lotId, caseId: c.caseId }))
    };

    forkJoin({
      original: this.caseService.queryOriginal(req),
      fixed:    this.caseService.queryFixed(req),
      batch:    this.caseService.queryBatch(req),
      twoPhase: this.caseService.queryTwoPhase(req)
    }).subscribe({
      next: ({ original, fixed, batch, twoPhase }) => {
        this.results = [
          { label: '① 原始版本',              tagClass: 'tag-bad',    noteClass: 'warning', response: original  },
          { label: '② Fixed Bucket',          tagClass: 'tag-warn',   noteClass: 'warning', response: fixed     },
          { label: '③ Plan A — Fixed Batch',  tagClass: 'tag-ok',     noteClass: 'success', response: batch     },
          { label: '④ Plan A+C — Two Phase',  tagClass: 'tag-best',   noteClass: 'success', response: twoPhase  },
        ];
        this.loading = false;
      },
      error: err => {
        this.errorMsg = err.error?.error ?? err.message ?? '伺服器錯誤';
        this.loading = false;
      }
    });
  }

  formatSeconds(sec: number): string {
    if (sec >= 3600) return `${(sec / 3600).toFixed(1)} 小時`;
    if (sec >= 60)   return `${(sec / 60).toFixed(1)} 分鐘`;
    return `${sec.toFixed(0)} 秒`;
  }

  fastest(): number {
    if (!this.results.length) return 0;
    return Math.min(...this.results.map(r => r.response.executionTimeMs));
  }

  speedup(ms: number): string {
    const base = this.results[0]?.response.executionTimeMs;
    if (!base || ms === 0) return '';
    const ratio = base / ms;
    return ratio >= 1 ? `快 ${ratio.toFixed(1)}x` : `慢 ${(ms / base).toFixed(1)}x`;
  }
}
