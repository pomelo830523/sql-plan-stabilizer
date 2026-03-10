import { Component } from '@angular/core';

@Component({
  selector: 'app-root',
  template: `
    <header class="header">
      <h1>EnhanceSQL — SQL Plan 穩定性測試</h1>
      <p>示範「固定 Bucket 參數數量」如何避免 Oracle hard-parse，提升查詢穩定性</p>
    </header>
    <main>
      <app-query-panel></app-query-panel>
    </main>
  `,
  styles: [`
    .header {
      background: linear-gradient(135deg, #1a237e, #283593);
      color: white;
      padding: 28px 40px;
      box-shadow: 0 2px 8px rgba(0,0,0,.3);
    }
    .header h1 { font-size: 1.6rem; margin-bottom: 6px; }
    .header p  { font-size: .9rem; opacity: .85; }
    main { padding: 32px 40px; max-width: 1200px; margin: 0 auto; }
  `]
})
export class AppComponent {}
