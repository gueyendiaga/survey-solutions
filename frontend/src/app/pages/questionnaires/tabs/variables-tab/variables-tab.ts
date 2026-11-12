import { DecimalPipe } from '@angular/common';
import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import type { EChartsCoreOption } from 'echarts/core';
import {
  HeadquartersInterviewAction,
  InterviewAnswer,
  QuestionAnalytics,
  QuestionSummary
} from '../../../../core/models/dashboard.models';
import { DashboardApiService } from '../../../../core/services/dashboard-api.service';
import { answerOptions } from '../../../../shared/utils/chart-options';
import { CompletenessStatusComponent } from '../../../../shared/components/completeness-status/completeness-status';
import { DashboardChartComponent } from '../../../../shared/components/dashboard-chart/dashboard-chart';
import { isSensitiveQuestion, statusList, typeLabel } from '../../../../shared/utils/labels';
import { LoadingStateComponent } from '../../../../shared/components/loading-state/loading-state';
import { apiErrorMessage, blobErrorDetail, saveFile } from '../../../../shared/utils/http-utils';
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
  protected readonly transcriptError = signal<string | null>(null);
  protected readonly downloadingTranscript = signal<string | null>(null);
  protected readonly detailInterview = signal<InterviewReviewRow | null>(null);
  protected readonly interviewAnswers = signal<InterviewAnswer[]>([]);
  private readonly rosterTitles = computed(() => {
    const titles = new Map<string, string>();
    for (const answer of this.interviewAnswers()) {
      const questionId = this.interviewAnswerQuestionId(answer)?.toLowerCase();
      const vector = this.rosterVector(answer);
      if (questionId && vector.length && answer.answer !== null && answer.answer !== undefined && answer.answer !== '') {
        titles.set(`${questionId}:${vector.join(',')}`, this.displayValue(answer.answer));
      }
    }
    return titles;
  });
  protected readonly interviewDetailsLoading = signal(false);
  protected readonly interviewDetailsError = signal<string | null>(null);
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

  protected downloadTranscript(interview: InterviewReviewRow): void {
    if (this.downloadingTranscript()) {
      return;
    }

    this.downloadingTranscript.set(interview.interviewId);
    this.transcriptError.set(null);
    this.api.interviewTranscript(interview.interviewId).subscribe({
      next: (file) => {
        this.downloadingTranscript.set(null);
        const key = (interview.interviewKey ?? interview.interviewId).replace(/[^a-zA-Z0-9._-]+/g, '-');
        saveFile(file, `interview-${key}.pdf`);
      },
      error: (error: unknown) => {
        this.downloadingTranscript.set(null);
        this.transcriptError.set(apiErrorMessage(error, 'La transcription PDF n’a pas pu être téléchargée.'));
        void blobErrorDetail(error).then((detail) => detail && this.transcriptError.set(detail));
      }
    });
  }

  protected openInterviewDetails(interview: InterviewReviewRow): void {
    this.detailInterview.set(interview);
    this.interviewAnswers.set([]);
    this.interviewDetailsError.set(null);
    this.interviewDetailsLoading.set(true);
    this.api.interviewAnswers(interview.interviewId).subscribe({
      next: (details) => {
        if (this.detailInterview()?.interviewId === interview.interviewId) {
          this.interviewAnswers.set(details.answers ?? []);
          this.interviewDetailsLoading.set(false);
        }
      },
      error: (error: unknown) => {
        if (this.detailInterview()?.interviewId === interview.interviewId) {
          this.interviewDetailsLoading.set(false);
          this.interviewDetailsError.set(apiErrorMessage(error, 'Les réponses de cette interview ne sont pas disponibles.'));
        }
      }
    });
  }

  protected closeInterviewDetails(): void {
    this.detailInterview.set(null);
    this.interviewDetailsError.set(null);
  }

  protected answerQuestion(answer: InterviewAnswer): QuestionSummary | undefined {
    return this.store.questions().find((question) => question.variableName === answer.variableName);
  }

  protected answerQuestionText(answer: InterviewAnswer): string {
    const question = this.answerQuestion(answer);
    const questionText = question?.questionText || question?.label || answer.variableName || 'Question sans libellé';
    const vector = this.rosterVector(answer);
    if (!vector.length || !questionText.toLowerCase().includes('%rostertitle%')) {
      return questionText;
    }

    const title = this.rosterTitle(answer, vector.length - 1);
    return questionText.replace(
      /%rostertitle%/gi,
      this.escapeHtml(title ?? `Élément n° ${vector[vector.length - 1]}`)
    );
  }

  protected rosterLabel(answer: InterviewAnswer): string | null {
    const vector = this.rosterVector(answer);
    if (!vector.length) {
      return null;
    }

    const elements = vector.map((index, depth) => {
      const title = this.rosterTitle(answer, depth);
      return title ? `« ${title} » (n° ${index})` : `élément n° ${index}`;
    });
    return `Dans le tableau : ${elements.join(' › ')}`;
  }

  private rosterTitle(answer: InterviewAnswer, depth: number): string | null {
    const questionId = this.answerQuestion(answer)?.rosterTitleQuestionIds?.[depth];
    const vector = this.rosterVector(answer);
    if (!questionId || !vector.length) {
      return null;
    }

    return this.rosterTitles().get(`${questionId.toLowerCase()}:${vector.slice(0, depth + 1).join(',')}`) ?? null;
  }

  private interviewAnswerQuestionId(answer: InterviewAnswer): string | null {
    const questionId = answer.additionalFields['QuestionId'];
    if (!questionId || typeof questionId !== 'object' || Array.isArray(questionId)) {
      return null;
    }

    const id = (questionId as Record<string, unknown>)['Id'];
    return typeof id === 'string' ? id : null;
  }

  private rosterVector(answer: InterviewAnswer): number[] {
    const questionId = answer.additionalFields['QuestionId'];
    if (!questionId || typeof questionId !== 'object' || Array.isArray(questionId)) {
      return [];
    }

    const vector = (questionId as Record<string, unknown>)['RosterVector'];
    if (!Array.isArray(vector) || !vector.every((index) => typeof index === 'number' && Number.isFinite(index))) {
      return [];
    }
    return vector as number[];
  }

  private escapeHtml(value: string): string {
    return value.replace(/[&<>"']/g, (character) => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;'
    })[character]!);
  }

  protected displayValue(value: unknown): string {
    if (value === null || value === undefined || value === '') {
      return '—';
    }
    if (Array.isArray(value)) {
      return value.map((item) => this.displayValue(item)).join(', ');
    }
    if (typeof value === 'object') {
      return Object.entries(value as Record<string, unknown>)
        .map(([key, item]) => `${this.fieldLabel(key)} : ${this.displayValue(item)}`)
        .join(' · ');
    }
    return String(value);
  }

  protected objectEntries(value: Record<string, unknown>): { key: string; value: unknown }[] {
    return Object.entries(value).map(([key, item]) => ({ key, value: item }));
  }

  protected fieldLabel(field: string): string {
    const labels: Record<string, string> = {
      QuestionId: 'Identifiant de question',
      Id: 'Identifiant',
      RosterVector: 'Ligne de tableau',
      VariableName: 'Variable',
      Answer: 'Réponse'
    };
    return labels[field] ?? field
      .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
      .replace(/^./, (letter) => letter.toLocaleUpperCase('fr'));
  }

  @HostListener('document:keydown.escape')
  protected closeDetailsOnEscape(): void {
    if (this.detailInterview()) {
      this.closeInterviewDetails();
    }
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
