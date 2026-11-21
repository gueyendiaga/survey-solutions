import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { QuestionnaireStore } from '../../questionnaire.store';
import { TablePagerComponent } from '../../../../shared/components/table-pager/table-pager';
import { TableSearchComponent } from '../../../../shared/components/table-search/table-search';
import { createTableState } from '../../../../shared/utils/table-state';

/** Onglet Rapports : rapport PDF à la demande et historique des rapports automatiques. */
@Component({
  selector: 'app-reports-tab',
  standalone: true,
  imports: [DatePipe, DecimalPipe, TablePagerComponent, TableSearchComponent],
  templateUrl: './reports-tab.html',
  styleUrl: './reports-tab.css'
})
export class ReportsTabComponent {
  protected readonly store = inject(QuestionnaireStore);
  protected readonly table = createTableState(() => this.store.archivedReports(), (report) => `${report.filename} ${report.generatedAt}`);
}
