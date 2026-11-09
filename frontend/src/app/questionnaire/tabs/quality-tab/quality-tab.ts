import { DecimalPipe } from '@angular/common';
import { Component, EventEmitter, Output, inject } from '@angular/core';
import { CompletenessStatusComponent } from '../../../shared/components/completeness-status/completeness-status';
import { statusList } from '../../../shared/utils/labels';
import { LoadingStateComponent } from '../../../shared/components/loading-state/loading-state';
import { QuestionnaireStore } from '../../questionnaire.store';

/** Onglet Qualité des données : complétude de chaque variable, de la plus faible à la meilleure. */
@Component({
  selector: 'app-quality-tab',
  standalone: true,
  imports: [DecimalPipe, LoadingStateComponent, CompletenessStatusComponent],
  templateUrl: './quality-tab.html',
  styleUrl: './quality-tab.css'
})
export class QualityTabComponent {
  protected readonly store = inject(QuestionnaireStore);
  protected readonly statusList = statusList;

  /** Demande l'ouverture de la variable dans l'onglet Variables. */
  @Output() readonly openVariable = new EventEmitter<string>();
}
