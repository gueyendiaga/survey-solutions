import { DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import type { EChartsCoreOption } from 'echarts/core';
import { QuestionAnalytics, QuestionSummary } from '../../../core/models/dashboard.models';
import { answerOptions } from '../../../shared/utils/chart-options';
import { CompletenessStatusComponent } from '../../../shared/components/completeness-status/completeness-status';
import { DashboardChartComponent } from '../../../shared/components/dashboard-chart/dashboard-chart';
import { isSensitiveQuestion, statusList, typeLabel } from '../../../shared/utils/labels';
import { LoadingStateComponent } from '../../../shared/components/loading-state/loading-state';
import { QuestionnaireStore } from '../../questionnaire.store';

/** Onglet Variables : liste filtrable, indicateurs, distribution et réponses individuelles. */
@Component({
  selector: 'app-variables-tab',
  standalone: true,
  imports: [DecimalPipe, FormsModule, DashboardChartComponent, LoadingStateComponent, CompletenessStatusComponent],
  templateUrl: './variables-tab.html',
  styleUrl: './variables-tab.css'
})
export class VariablesTabComponent {
  protected readonly store = inject(QuestionnaireStore);
  protected readonly statusList = statusList;
  protected readonly typeLabel = typeLabel;
  protected readonly isSensitiveQuestion = isSensitiveQuestion;

  protected chartOptions(analytics: QuestionAnalytics): EChartsCoreOption {
    return answerOptions(analytics, this.store.chartType());
  }

  protected hasSpecialValues(analytics: QuestionAnalytics): boolean {
    return (analytics.specialValues ?? []).length > 0;
  }

  protected retry(): void {
    const question: QuestionSummary | null = this.store.selectedQuestion();
    if (this.store.questionsError() || !question) {
      this.store.reloadQuestions();
    } else {
      this.store.selectQuestion(question);
    }
  }
}
