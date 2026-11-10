import { DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import type { EChartsCoreOption } from 'echarts/core';
import {
  HeadquartersInterviewAction,
  QuestionAnalytics,
  QuestionSummary
} from '../../../core/models/dashboard.models';
import { DashboardApiService } from '../../../core/services/dashboard-api.service';
import { answerOptions } from '../../../shared/utils/chart-options';
import { CompletenessStatusComponent } from '../../../shared/components/completeness-status/completeness-status';
import { DashboardChartComponent } from '../../../shared/components/dashboard-chart/dashboard-chart';
import { isSensitiveQuestion, statusList, typeLabel } from '../../../shared/utils/labels';
import { LoadingStateComponent } from '../../../shared/components/loading-state/loading-state';
import { apiErrorMessage } from '../../../shared/utils/http-utils';
import { QuestionnaireStore } from '../../questionnaire.store';

interface InterviewReviewRow {
  interviewId: string;
  interviewKey: string | null;
  status: string;
}

interface PendingInterviewAction {
  interview: InterviewReviewRow;
  action: HeadquartersInterviewAction;
}

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
  private readonly api = inject(DashboardApiService);
  protected readonly statusList = statusList;
  protected readonly typeLabel = typeLabel;
  protected readonly isSensitiveQuestion = isSensitiveQuestion;
  protected readonly pendingAction = signal<PendingInterviewAction | null>(null);
  protected readonly savingAction = signal(false);
  protected readonly actionError = signal<string | null>(null);
  protected readonly actionSuccess = signal<string | null>(null);
  protected comment = '';

  protected interviews(analytics: QuestionAnalytics): InterviewReviewRow[] {
    const unique = new Map<string, InterviewReviewRow>();
    for (const observation of analytics.observations) {
      unique.set(observation.interviewId, {
        interviewId: observation.interviewId,
        interviewKey: observation.interviewKey,
        status: observation.status
      });
    }
    return [...unique.values()];
  }

  protected interviewStatusLabel(status: string): string {
    return ({
      Completed: 'Terminée',
      ApprovedBySupervisor: 'Approuvée par le superviseur',
      ApprovedByHeadquarters: 'Approuvée par HQ',
      RejectedBySupervisor: 'Rejetée par le superviseur',
      RejectedByHeadquarters: 'Rejetée par HQ'
    } as Record<string, string>)[status] ?? status;
  }

  protected canApproveOrReject(status: string): boolean {
    return status === 'Completed' || status === 'ApprovedBySupervisor';
  }

  protected openAction(interview: InterviewReviewRow, action: HeadquartersInterviewAction): void {
    this.comment = '';
    this.actionError.set(null);
    this.actionSuccess.set(null);
    this.pendingAction.set({ interview, action });
  }

  protected cancelAction(): void {
    if (!this.savingAction()) {
      this.pendingAction.set(null);
    }
  }

  protected actionTitle(action: HeadquartersInterviewAction): string {
    return action === 'hqapprove' ? 'Approuver l’interview'
      : action === 'hqreject' ? 'Rejeter l’interview'
      : 'Retirer l’approbation HQ';
  }

  protected actionDescription(action: HeadquartersInterviewAction): string {
    return action === 'hqapprove'
      ? 'L’interview sera marquée comme approuvée par Headquarters.'
      : action === 'hqreject'
        ? 'L’interview sera rejetée par Headquarters et renvoyée pour correction.'
        : 'L’interview ne sera plus approuvée par Headquarters et repassera au statut approuvé par le superviseur.';
  }

  protected confirmAction(): void {
    const pending = this.pendingAction();
    const questionnaire = this.store.questionnaire();
    if (!pending || !questionnaire || this.savingAction()) {
      return;
    }

    this.savingAction.set(true);
    this.actionError.set(null);
    this.api.interviewAction(
      questionnaire.questionnaireId,
      questionnaire.version,
      pending.interview.interviewId,
      pending.action,
      this.comment
    ).subscribe({
      next: (result) => {
        this.savingAction.set(false);
        this.pendingAction.set(null);
        const key = pending.interview.interviewKey;
        this.actionSuccess.set(
          `${key ? `Interview ${key} : ` : 'Interview traitée : '}statut mis à jour (${this.interviewStatusLabel(result.status)}).`
        );
        this.store.refreshAfterInterviewAction();
      },
      error: (error: unknown) => {
        this.savingAction.set(false);
        this.actionError.set(apiErrorMessage(error, 'HQ n’a pas pu appliquer cette action.'));
      }
    });
  }

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
