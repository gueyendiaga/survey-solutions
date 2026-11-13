import { DatePipe } from '@angular/common';
import { Component, HostListener, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SearchSelectComponent, type SearchSelectOption } from '../../../../shared/components/search-select/search-select';
import {
  HeadquartersInterviewAction,
  InterviewAnswer,
  InterviewHistory,
  InterviewReviewItem,
  InterviewReviewPage,
  InterviewStatistics
} from '../../../../core/models/dashboard.models';
import { DashboardApiService } from '../../../../core/services/dashboard-api.service';
import { apiErrorMessage, saveFile } from '../../../../shared/utils/http-utils';
import { LoadingStateComponent } from '../../../../shared/components/loading-state/loading-state';
import { QuestionnaireStore } from '../../questionnaire.store';

const REVIEW_STATUSES = [
  'Completed',
  'ApprovedBySupervisor',
  'RejectedBySupervisor',
  'RejectedByHeadquarters',
  'ApprovedByHeadquarters',
  'InterviewerAssigned',
  'SupervisorAssigned',
  'Restarted',
  'ReadyForInterview',
  'SentToCapi',
  'Created',
  'Restored'
];
const REVIEW_STATUS_LABELS: Record<string, string> = {
  Completed: 'Terminée',
  ApprovedBySupervisor: 'Approuvée · superviseur',
  RejectedBySupervisor: 'Rejetée · superviseur',
  RejectedByHeadquarters: 'Rejetée · HQ',
  ApprovedByHeadquarters: 'Approuvée · HQ',
  InterviewerAssigned: 'Affectée · enquêteur',
  SupervisorAssigned: 'Affectée · superviseur',
  Restarted: 'Redémarrée',
  ReadyForInterview: 'Prête à démarrer',
  SentToCapi: 'Envoyée sur tablette',
  Created: 'Créée',
  Restored: 'Restaurée'
};
const PAGE_SIZE = 20;

@Component({
  selector: 'app-interview-review',
  standalone: true,
  imports: [DatePipe, FormsModule, LoadingStateComponent, SearchSelectComponent],
  templateUrl: './interview-review.html'
})
export class InterviewReviewComponent implements OnInit, OnDestroy {
  protected readonly store = inject(QuestionnaireStore);
  private readonly api = inject(DashboardApiService);

  protected readonly statusOptions: SearchSelectOption[] = [
    { value: 'pending', label: 'À traiter' },
    { value: 'all', label: 'Tous les statuts' },
    ...REVIEW_STATUSES.map((status) => ({
      value: status,
      label: REVIEW_STATUS_LABELS[status]
    }))
  ];
  protected readonly queue = signal<InterviewReviewPage | null>(null);
  protected readonly loading = signal(true);
  protected readonly queueError = signal<string | null>(null);
  protected readonly search = signal('');
  protected readonly selectedStatus = signal('pending');
  protected readonly detail = signal<InterviewReviewItem | null>(null);
  protected readonly answers = signal<InterviewAnswer[]>([]);
  protected readonly answersLoading = signal(false);
  protected readonly answersError = signal<string | null>(null);
  protected readonly statistics = signal<InterviewStatistics | null>(null);
  protected readonly statisticsLoading = signal(false);
  protected readonly statisticsError = signal<string | null>(null);
  protected readonly history = signal<InterviewHistory | null>(null);
  protected readonly historyLoading = signal(false);
  protected readonly historyError = signal<string | null>(null);
  protected readonly action = signal<HeadquartersInterviewAction | null>(null);
  protected readonly actionComment = signal('');
  protected readonly actionError = signal<string | null>(null);
  protected readonly actionSaving = signal(false);
  protected readonly actionSuccess = signal<string | null>(null);
  protected readonly responsibleOptions = signal<SearchSelectOption[]>([]);
  protected readonly responsiblesLoading = signal(false);
  protected readonly responsiblesError = signal<string | null>(null);
  protected readonly responsibleId = signal('');
  protected readonly commentTarget = signal<number | null>(null);
  protected readonly commentDraft = signal('');
  protected readonly commentSaving = signal(false);
  protected readonly commentFeedback = signal<{ index: number; text: string; error: boolean } | null>(null);
  protected readonly transcriptLoading = signal(false);

  protected page = 1;
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  private queueRequest = 0;
  private responsiblesLoaded = false;

  ngOnInit(): void {
    this.loadQueue();
  }

  ngOnDestroy(): void {
    if (this.searchTimer !== null) {
      clearTimeout(this.searchTimer);
    }
  }

  protected onSearchChange(value: string): void {
    this.search.set(value);
    this.page = 1;
    if (this.searchTimer !== null) {
      clearTimeout(this.searchTimer);
    }
    this.searchTimer = setTimeout(() => this.loadQueue(), 250);
  }

  protected onStatusChange(value: string): void {
    this.selectedStatus.set(value);
    this.page = 1;
    this.loadQueue();
  }

  protected refresh(): void {
    this.loadQueue();
  }

  protected setPage(page: number): void {
    const lastPage = this.pageCount();
    if (page >= 1 && page <= lastPage && page !== this.page) {
      this.page = page;
      this.loadQueue();
    }
  }

  protected pageCount(): number {
    return Math.max(1, Math.ceil((this.queue()?.totalCount ?? 0) / PAGE_SIZE));
  }

  protected rangeStart(): number {
    return this.queue()?.totalCount ? (this.page - 1) * PAGE_SIZE + 1 : 0;
  }

  protected rangeEnd(): number {
    return Math.min(this.page * PAGE_SIZE, this.queue()?.totalCount ?? 0);
  }

  protected statusLabel(status: string): string {
    return REVIEW_STATUS_LABELS[status] ?? status;
  }

  protected statusTone(status: string): string {
    if (status === 'ApprovedByHeadquarters' || status === 'ApprovedBySupervisor') {
      return 'bg-emerald-50 text-emerald-700 ring-emerald-200';
    }
    if (status.includes('Rejected')) {
      return 'bg-rose-50 text-rose-700 ring-rose-200';
    }
    if (status === 'Completed') {
      return 'bg-[var(--primary-soft)] text-[var(--primary)] ring-[var(--primary-line)]';
    }
    return 'bg-slate-100 text-slate-600 ring-slate-200';
  }

  protected actionAvailable(status: string): boolean {
    return status === 'Completed' || status === 'ApprovedBySupervisor' || status === 'ApprovedByHeadquarters';
  }

  protected actionFor(status: string): HeadquartersInterviewAction | null {
    if (status === 'Completed' || status === 'ApprovedBySupervisor') {
      return 'hqapprove';
    }
    if (status === 'ApprovedByHeadquarters') {
      return 'hqunapprove';
    }
    return null;
  }

  protected actionTitle(action: HeadquartersInterviewAction): string {
    return action === 'hqapprove' ? 'Approuver l’interview'
      : action === 'hqreject' ? 'Rejeter l’interview'
      : 'Retirer l’approbation HQ';
  }

  protected openDetail(interview: InterviewReviewItem): void {
    this.detail.set(interview);
    this.answers.set([]);
    this.answersError.set(null);
    this.answersLoading.set(true);
    this.statistics.set(null);
    this.statisticsError.set(null);
    this.statisticsLoading.set(true);
    this.history.set(null);
    this.historyError.set(null);
    this.historyLoading.set(true);
    this.action.set(null);
    this.actionError.set(null);
    this.actionSuccess.set(null);
    this.commentFeedback.set(null);

    this.api.interviewAnswers(interview.interviewId).subscribe({
      next: (result) => {
        if (this.detail()?.interviewId === interview.interviewId) {
          this.answers.set(result.answers ?? []);
          this.answersLoading.set(false);
        }
      },
      error: (error: unknown) => {
        if (this.detail()?.interviewId === interview.interviewId) {
          this.answersLoading.set(false);
          this.answersError.set(apiErrorMessage(error, 'Les réponses de cette interview ne sont pas disponibles.'));
        }
      }
    });
    this.loadStatistics(interview.interviewId);
    this.loadHistory(interview.interviewId);
  }

  protected closeDetail(): void {
    this.detail.set(null);
    this.action.set(null);
    this.commentTarget.set(null);
  }

  @HostListener('document:keydown.escape')
  protected closeOnEscape(): void {
    if (this.detail() && !this.actionSaving() && !this.commentSaving()) {
      this.closeDetail();
    }
  }

  protected openAction(action: HeadquartersInterviewAction): void {
    this.action.set(action);
    this.actionComment.set('');
    this.actionError.set(null);
    this.responsibleId.set('');
    this.responsiblesError.set(null);
    this.actionSuccess.set(null);
    if (action === 'hqreject' && !this.responsiblesLoaded && !this.responsiblesLoading()) {
      this.responsiblesLoading.set(true);
      this.api.reviewResponsibles().subscribe({
        next: (responsibles) => {
          this.responsibleOptions.set([
            { value: null, label: 'Conserver le responsable actuel' },
            ...responsibles.map((responsible) => ({
              value: responsible.userId,
              label: responsible.userName
            }))
          ]);
          this.responsiblesLoaded = true;
          this.responsiblesLoading.set(false);
        },
        error: (error: unknown) => {
          this.responsiblesLoading.set(false);
          this.responsiblesError.set(apiErrorMessage(error, 'La liste des enquêteurs n’a pas pu être chargée.'));
        }
      });
    }
  }

  protected cancelAction(): void {
    if (!this.actionSaving()) {
      this.action.set(null);
    }
  }

  protected confirmAction(): void {
    const interview = this.detail();
    const questionnaire = this.store.questionnaire();
    const action = this.action();
    if (!interview || !questionnaire || !action || this.actionSaving()) {
      return;
    }
    this.actionSaving.set(true);
    this.actionError.set(null);
    this.api.interviewAction(
      questionnaire.questionnaireId,
      questionnaire.version,
      interview.interviewId,
      action,
      this.actionComment(),
      action === 'hqreject' ? this.responsibleId() || null : null
    ).subscribe({
      next: (result) => {
        this.actionSaving.set(false);
        this.action.set(null);
        this.actionSuccess.set(`Statut mis à jour : ${this.statusLabel(result.status)}.`);
        this.detail.update((current) => current
          ? { ...current, status: result.status }
          : current);
        this.loadQueue();
        this.loadHistory(interview.interviewId);
        this.store.refreshAfterInterviewAction();
      },
      error: (error: unknown) => {
        this.actionSaving.set(false);
        this.actionError.set(apiErrorMessage(error, 'HQ n’a pas pu appliquer cette action.'));
      }
    });
  }

  protected beginComment(index: number): void {
    const target = this.commentTarget() === index ? null : index;
    this.commentTarget.set(target);
    this.commentDraft.set('');
    this.commentFeedback.set(null);
  }

  protected sendComment(answer: InterviewAnswer, index: number): void {
    const interview = this.detail();
    const variableName = answer.variableName?.trim();
    const comment = this.commentDraft().trim();
    if (!interview || !variableName || !comment || this.commentSaving()) {
      return;
    }
    this.commentSaving.set(true);
    this.commentFeedback.set(null);
    this.api.commentOnInterviewQuestion(interview.interviewId, {
      variableName,
      rosterVector: this.rosterVector(answer),
      comment
    }).subscribe({
      next: () => {
        this.commentSaving.set(false);
        this.commentTarget.set(null);
        this.commentDraft.set('');
        this.commentFeedback.set({ index, text: 'Commentaire transmis à HQ.', error: false });
        this.loadStatistics(interview.interviewId);
        this.loadHistory(interview.interviewId);
      },
      error: (error: unknown) => {
        this.commentSaving.set(false);
        this.commentFeedback.set({
          index,
          text: apiErrorMessage(error, 'Le commentaire n’a pas pu être envoyé à HQ.'),
          error: true
        });
      }
    });
  }

  protected downloadTranscript(): void {
    const interview = this.detail();
    if (!interview || this.transcriptLoading()) {
      return;
    }
    this.transcriptLoading.set(true);
    this.api.interviewTranscript(interview.interviewId).subscribe({
      next: (file) => {
        this.transcriptLoading.set(false);
        const key = (interview.interviewKey || interview.interviewId).replace(/[^a-zA-Z0-9._-]+/g, '-');
        saveFile(file, `interview-${key}.pdf`);
      },
      error: (error: unknown) => {
        this.transcriptLoading.set(false);
        this.answersError.set(apiErrorMessage(error, 'La transcription PDF n’a pas pu être téléchargée.'));
      }
    });
  }

  protected answerTitle(answer: InterviewAnswer): string {
    const question = this.store.questions().find((item) => item.variableName === answer.variableName);
    return question?.questionText || question?.label || answer.variableName || 'Question sans libellé';
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

  protected historyActionLabel(action: string): string {
    const labels: Record<string, string> = {
      AnswerSet: 'Réponse enregistrée',
      AnswerRemoved: 'Réponse effacée',
      CommentSet: 'Commentaire ajouté',
      Completed: 'Interview terminée',
      Restarted: 'Interview redémarrée',
      ApproveBySupervisor: 'Approuvée par le superviseur',
      ApproveByHeadquarter: 'Approuvée par HQ',
      RejectedBySupervisor: 'Rejetée par le superviseur',
      RejectedByHeadquarter: 'Rejetée par HQ',
      UnapproveByHeadquarters: 'Approbation HQ retirée',
      InterviewerAssigned: 'Affectée à un enquêteur',
      SupervisorAssigned: 'Affectée à un superviseur',
      InterviewCreated: 'Interview créée',
      InterviewModeChanged: 'Mode de collecte modifié',
      Paused: 'Collecte mise en pause',
      Resumed: 'Collecte reprise'
    };
    return labels[action] ?? action.replace(/([a-z])([A-Z])/g, '$1 $2');
  }

  protected objectEntries(value: Record<string, string | null> | null): { key: string; value: string | null }[] {
    return Object.entries(value ?? {}).map(([key, item]) => ({ key, value: item }));
  }

  protected fieldLabel(field: string): string {
    return field.replace(/([a-z0-9])([A-Z])/g, '$1 $2').replace(/^./, (letter) => letter.toLocaleUpperCase('fr'));
  }

  protected duration(seconds: number | null): string {
    if (seconds === null || seconds < 0) {
      return '—';
    }
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);
    return hours ? `${hours} h ${minutes % 60} min` : `${minutes} min`;
  }

  protected progressPercent(stats: InterviewStatistics): number {
    const total = (stats.answered ?? 0) + (stats.notAnswered ?? 0);
    return total ? Math.round((stats.answered ?? 0) / total * 100) : 0;
  }

  private loadQueue(): void {
    const questionnaire = this.store.questionnaire();
    if (!questionnaire) {
      return;
    }
    const requestId = ++this.queueRequest;
    const status = this.selectedStatus();
    this.loading.set(true);
    this.queueError.set(null);
    this.api.interviewReview(questionnaire.questionnaireId, questionnaire.version, {
      status: status === 'pending' || status === 'all' ? null : status,
      pendingOnly: status === 'pending',
      search: this.search(),
      page: this.page,
      pageSize: PAGE_SIZE
    }).subscribe({
      next: (page) => {
        if (requestId === this.queueRequest) {
          this.queue.set(page);
          this.loading.set(false);
        }
      },
      error: (error: unknown) => {
        if (requestId === this.queueRequest) {
          this.queueError.set(apiErrorMessage(error, 'La file de revue n’a pas pu être chargée.'));
          this.loading.set(false);
        }
      }
    });
  }

  private loadStatistics(interviewId: string): void {
    this.statisticsLoading.set(true);
    this.statisticsError.set(null);
    this.api.interviewStatistics(interviewId).subscribe({
      next: (result) => {
        if (this.detail()?.interviewId === interviewId) {
          this.statistics.set(result);
          this.statisticsLoading.set(false);
        }
      },
      error: (error: unknown) => {
        if (this.detail()?.interviewId === interviewId) {
          this.statisticsError.set(apiErrorMessage(error, 'Les statistiques de cette interview ne sont pas disponibles.'));
          this.statisticsLoading.set(false);
        }
      }
    });
  }

  private loadHistory(interviewId: string): void {
    this.historyLoading.set(true);
    this.historyError.set(null);
    this.api.interviewHistory(interviewId).subscribe({
      next: (result) => {
        if (this.detail()?.interviewId === interviewId) {
          this.history.set(result);
          this.historyLoading.set(false);
        }
      },
      error: (error: unknown) => {
        if (this.detail()?.interviewId === interviewId) {
          this.historyError.set(apiErrorMessage(error, 'L’historique de cette interview n’est pas disponible.'));
          this.historyLoading.set(false);
        }
      }
    });
  }

  private rosterVector(answer: InterviewAnswer): number[] {
    const questionId = answer.additionalFields['QuestionId'];
    if (!questionId || typeof questionId !== 'object' || Array.isArray(questionId)) {
      return [];
    }
    const vector = (questionId as Record<string, unknown>)['RosterVector'];
    return Array.isArray(vector) && vector.every((value) => typeof value === 'number' && Number.isInteger(value))
      ? vector
      : [];
  }
}
