import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule } from '@angular/common/http';
import { ReactiveFormsModule } from '@angular/forms';

import { AppComponent } from './app.component';
import { QueryPanelComponent } from './components/query-panel/query-panel.component';
import { ResultTableComponent } from './components/result-table/result-table.component';

@NgModule({
  declarations: [
    AppComponent,
    QueryPanelComponent,
    ResultTableComponent
  ],
  imports: [
    BrowserModule,
    HttpClientModule,
    ReactiveFormsModule
  ],
  bootstrap: [AppComponent]
})
export class AppModule {}
