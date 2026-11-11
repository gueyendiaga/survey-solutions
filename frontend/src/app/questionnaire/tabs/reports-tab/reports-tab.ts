import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { QuestionnaireStore } from '../../questionnaire.store';

/** Onglet Rapports : rapport PDF à la demande et historique des rapports automatiques. */
@Component({
  selector: 'app-reports-tab',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
  templateUrl: './reports-tab.html',
  styleUrl: './reports-tab.css'
})
export class ReportsTabComponent {
  protected readonly store = inject(QuestionnaireStore);
}
