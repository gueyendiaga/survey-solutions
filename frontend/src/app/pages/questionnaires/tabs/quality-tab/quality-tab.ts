import { DecimalPipe } from '@angular/common';
import { Component, EventEmitter, Output, inject } from '@angular/core';
import { CompletenessStatusComponent } from '../../../../shared/components/completeness-status/completeness-status';
import { InfoTooltipComponent } from '../../../../shared/components/info-tooltip/info-tooltip';
import { statusList } from '../../../../shared/utils/labels';
import { LoadingStateComponent } from '../../../../shared/components/loading-state/loading-state';
import { KpiCardComponent } from '../../../../shared/components/kpi-card/kpi-card';
import { TablePagerComponent } from '../../../../shared/components/table-pager/table-pager';
import { TableSearchComponent } from '../../../../shared/components/table-search/table-search';
import { htmlToText } from '../../../../shared/utils/html-text';
import { createTableState } from '../../../../shared/utils/table-state';
import { QuestionnaireStore } from '../../questionnaire.store';

/** Onglet Qualité des données : complétude de chaque variable, de la plus faible à la meilleure. */
@Component({
  selector: 'app-quality-tab',
  standalone: true,
  imports: [
    DecimalPipe,
    LoadingStateComponent,
    CompletenessStatusComponent,
    InfoTooltipComponent,
    KpiCardComponent,
    TablePagerComponent,
    TableSearchComponent
  ],
  templateUrl: './quality-tab.html',
  styleUrl: './quality-tab.css'
})
export class QualityTabComponent {
  protected readonly store = inject(QuestionnaireStore);
  protected readonly statusList = statusList;
  /** Variables de la plus faible à la meilleure complétude, recherche sur le nom et le libellé. */
  protected readonly table = createTableState(
    () => this.store.qualityRows(),
    (item) => `${item.question.variableName} ${htmlToText(item.question.label || item.question.questionText)}`
  );
  protected readonly averageRateHelp =
    'Moyenne simple des taux de réponse des variables posées : somme des taux ÷ nombre de variables posées. Chaque variable a le même poids; les variables sans réponse attendue sont exclues.';
  protected readonly responseRateHelp =
    'Réponses ÷ (réponses + sans réponse) × 100. Les non-applicables sont exclus; pour un roster, chaque ligne compte comme une unité.';
  protected readonly lowRateHelp =
    'Nombre de variables posées dont le taux de réponse est strictement inférieur à 80 %.';

  /** Demande l'ouverture de la variable dans l'onglet Variables. */
  @Output() readonly openVariable = new EventEmitter<string>();
}
