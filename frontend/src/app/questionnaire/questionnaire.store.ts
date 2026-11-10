import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { DashboardApiService } from '../core/services/dashboard-api.service';
import {
  AnalysisFilters,
  ArchivedReport,
  CompletenessOverview,
  FieldworkOverview,
  FilterOptions,
  NO_FILTERS,
  QuestionAnalytics,
  QuestionCompleteness,
  QuestionOption,
  Questionnaire,
  QuestionSummary,
  SyncStatus
} from '../core/models/dashboard.models';
import { AnswerChartType } from '../shared/utils/chart-options';
import { apiErrorMessage, blobErrorDetail, isSyncPending, saveFile } from '../shared/utils/http-utils';
import { VARIABLE_KIND_LABELS, VariableKind, variableKind } from '../shared/utils/labels';
import { QuestionnaireTab, TabBadges } from './questionnaire-tabs';

const SYNC_POLL_INTERVAL_MS = 3000;

/**
 * État et logique d'un questionnaire ouvert : synchronisation avec HQ, filtres, suivi terrain,
 * variables, qualité, rapports et exports. Fourni par la page du questionnaire : une instance
 * par questionnaire affiché, détruite avec la page.
 */
@Injectable()
export class QuestionnaireStore {
  private readonly api = inject(DashboardApiService);

  readonly questionnaire = signal<Questionnaire | null>(null);
  readonly tab = signal<QuestionnaireTab>('fieldwork');

  readonly syncStatus = signal<SyncStatus | null>(null);
  readonly syncRequesting = signal(false);

  readonly filters = signal<AnalysisFilters>(NO_FILTERS);
  readonly filterOptions = signal<FilterOptions | null>(null);
  readonly variableOptions = signal<QuestionOption[]>([]);

  readonly fieldwork = signal<FieldworkOverview | null>(null);
  readonly fieldworkError = signal<string | null>(null);

  readonly questions = signal<QuestionSummary[]>([]);
  readonly questionsLoading = signal(false);
  readonly questionsError = signal<string | null>(null);
  readonly selectedQuestion = signal<QuestionSummary | null>(null);
  readonly analytics = signal<QuestionAnalytics | null>(null);
  readonly analyticsLoading = signal(false);
  readonly analyticsError = signal<string | null>(null);
  readonly chartType = signal<AnswerChartType>('bar');
  readonly variableSearch = signal('');
  readonly variableType = signal<VariableKind | 'all'>('all');

  readonly completeness = signal<CompletenessOverview | null>(null);
  readonly completenessError = signal<string | null>(null);

  readonly archivedReports = signal<ArchivedReport[]>([]);
  readonly reportGenerating = signal(false);
  readonly exporting = signal<'csv' | 'xlsx' | null>(null);
  readonly downloadError = signal<string | null>(null);

  private fieldworkLoading = false;
  private syncPollTimer: ReturnType<typeof setTimeout> | null = null;

  /** Badges des onglets : nombre de variables, variables à examiner, rapports archivés. */
  readonly tabBadges = computed<TabBadges>(() => ({
    variables: this.questions().length || null,
    quality: (this.completeness()?.variables ?? []).filter((item) => item.missingCount > 0).length || null,
    reports: this.archivedReports().length || null
  }));

  readonly filteredQuestions = computed(() => {
    const term = this.variableSearch().trim().toLowerCase();
    const kind = this.variableType();
    return this.questions().filter((question) =>
      (kind === 'all' || variableKind(question) === kind)
      && (!term || `${question.variableName} ${question.label} ${question.questionText}`.toLowerCase().includes(term)));
  });

  /** Filtres par type proposés au-dessus de la liste (seuls les types présents). */
  readonly variableKinds = computed(() => {
    const counts = new Map<VariableKind, number>();
    this.questions().forEach((question) => counts.set(variableKind(question), (counts.get(variableKind(question)) ?? 0) + 1));
    return (['choice', 'numeric', 'text', 'other'] as VariableKind[])
      .filter((kind) => counts.has(kind))
      .map((kind) => ({ kind, label: VARIABLE_KIND_LABELS[kind], count: counts.get(kind) ?? 0 }));
  });

  /** Variables triées du taux de réponse le plus faible au plus élevé (onglet Qualité). */
  readonly qualityRows = computed(() =>
    [...(this.completeness()?.variables ?? [])].sort((a, b) => {
      const posedA = a.answeredCount + a.missingCount > 0;
      const posedB = b.answeredCount + b.missingCount > 0;
      if (posedA !== posedB) {
        return posedA ? -1 : 1;
      }
      return a.responseRate - b.responseRate || a.question.variableName.localeCompare(b.question.variableName);
    })
  );

  readonly qualitySummary = computed(() => {
    const rows = this.completeness()?.variables ?? [];
    const posed = rows.filter((row) => row.answeredCount + row.missingCount > 0);
    return {
      variables: rows.length,
      posed: posed.length,
      average: posed.length ? posed.reduce((sum, row) => sum + row.responseRate, 0) / posed.length : null,
      low: posed.filter((row) => row.responseRate < 80).length,
      missing: rows.reduce((sum, row) => sum + row.missingCount, 0)
    };
  });

  /** Sous-titre de l'onglet courant, tiré des données chargées. */
  readonly lead = computed(() => {
    switch (this.tab()) {
      case 'fieldwork':
        return this.fieldworkLead();
      case 'variables': {
        const count = this.completeness()?.interviewCount;
        return count === undefined ? 'Indicateurs par question'
          : `Indicateurs par question · ${count.toLocaleString('fr-FR')} interview(s) analysée(s)`;
      }
      case 'quality':
        return 'Complétude, non-réponses et questions non applicables par variable';
      default:
        return 'Rapports PDF à la demande et archivés chaque matin';
    }
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stopSyncPolling());
  }

  /** Ouvre un questionnaire : réinitialise l'état puis charge ses données. */
  open(questionnaire: Questionnaire): void {
    if (this.isCurrent(questionnaire)) {
      return;
    }
    this.stopSyncPolling();
    this.questionnaire.set(questionnaire);
    this.syncStatus.set(null);
    this.filters.set(NO_FILTERS);
    this.filterOptions.set(null);
    this.variableOptions.set([]);
    this.fieldwork.set(null);
    this.fieldworkError.set(null);
    this.questions.set([]);
    this.selectedQuestion.set(null);
    this.analytics.set(null);
    this.completeness.set(null);
    this.completenessError.set(null);
    this.archivedReports.set([]);
    this.downloadError.set(null);
    this.variableSearch.set('');
    this.variableType.set('all');

    this.loadSyncStatus(questionnaire);
    this.loadFilterOptions(questionnaire);
    if (this.tab() === 'fieldwork') {
      this.loadFieldwork(questionnaire);
    }
    // Chargés dès l'ouverture : ils alimentent aussi le badge de l'onglet Rapports.
    this.loadArchivedReports(questionnaire);
    this.loadQuestions(questionnaire);
  }

  /** Active un onglet et charge ce qui lui manque. */
  setTab(tab: QuestionnaireTab): void {
    this.tab.set(tab);
    const questionnaire = this.questionnaire();
    if (!questionnaire) {
      return;
    }
    if (tab === 'fieldwork' && !this.fieldwork() && !this.fieldworkLoading) {
      this.loadFieldwork(questionnaire);
    }
    if (tab === 'reports') {
      this.loadArchivedReports(questionnaire);
    }
  }

  // ---- Synchronisation ---------------------------------------------------------------

  /** Synchronisation manuelle : export HQ puis import, suivie jusqu'à la fin. */
  startSync(): void {
    const questionnaire = this.questionnaire();
    if (!questionnaire || this.syncRequesting()) {
      return;
    }
    this.syncRequesting.set(true);
    this.api.startSync(questionnaire.questionnaireId, questionnaire.version).subscribe({
      next: (status) => {
        this.syncRequesting.set(false);
        this.syncStatus.set({ ...status, inProgress: true });
        this.scheduleSyncPoll(questionnaire);
      },
      error: (error: unknown) => {
        this.syncRequesting.set(false);
        this.downloadError.set(apiErrorMessage(error, 'Impossible de lancer la synchronisation.'));
      }
    });
  }

  private loadSyncStatus(questionnaire: Questionnaire): void {
    this.api.syncStatus(questionnaire.questionnaireId, questionnaire.version).subscribe({
      next: (status) => {
        if (!this.isCurrent(questionnaire)) {
          return;
        }
        this.syncStatus.set(status);
        if (status.inProgress) {
          this.scheduleSyncPoll(questionnaire);
        }
      },
      error: () => this.syncStatus.set(null)
    });
  }

  private scheduleSyncPoll(questionnaire: Questionnaire): void {
    this.stopSyncPolling();
    this.syncPollTimer = setTimeout(() => this.pollSync(questionnaire), SYNC_POLL_INTERVAL_MS);
  }

  private pollSync(questionnaire: Questionnaire): void {
    this.syncPollTimer = null;
    if (!this.isCurrent(questionnaire)) {
      return;
    }
    const previousSuccess = this.syncStatus()?.lastSuccessAt ?? null;
    this.api.syncStatus(questionnaire.questionnaireId, questionnaire.version).subscribe({
      next: (status) => {
        if (!this.isCurrent(questionnaire)) {
          return;
        }
        this.syncStatus.set(status);
        if (status.inProgress) {
          this.scheduleSyncPoll(questionnaire);
        } else if (status.lastSuccessAt && status.lastSuccessAt !== previousSuccess) {
          this.reloadAnalyses(questionnaire);
        }
      },
      error: () => this.scheduleSyncPoll(questionnaire)
    });
  }

  private stopSyncPolling(): void {
    if (this.syncPollTimer !== null) {
      clearTimeout(this.syncPollTimer);
      this.syncPollTimer = null;
    }
  }

  /** Données en cours d'import : on affiche l'attente et on surveille la fin de l'import. */
  private handleSyncPending(error: unknown, questionnaire: Questionnaire): boolean {
    if (!isSyncPending(error)) {
      return false;
    }
    const current = this.syncStatus();
    this.syncStatus.set({
      inProgress: true,
      lastSuccessAt: current?.lastSuccessAt ?? null,
      interviewCount: current?.interviewCount ?? null,
      lastStatus: 'RUNNING',
      lastError: null
    });
    if (this.syncPollTimer === null) {
      this.scheduleSyncPoll(questionnaire);
    }
    return true;
  }

  private reloadAnalyses(questionnaire: Questionnaire): void {
    this.loadFilterOptions(questionnaire);
    if (this.tab() === 'fieldwork') {
      this.loadFieldwork(questionnaire);
    } else {
      this.fieldwork.set(null);
    }
    this.loadCompleteness(questionnaire);
    this.reloadSelectedQuestion();
  }

  // ---- Filtres -----------------------------------------------------------------------

  /** Applique les filtres au suivi terrain, à la complétude et à la variable sélectionnée. */
  applyFilters(filters: AnalysisFilters): void {
    this.filters.set(filters);
    const questionnaire = this.questionnaire();
    if (!questionnaire) {
      return;
    }
    this.fieldwork.set(null);
    if (this.tab() === 'fieldwork') {
      this.loadFieldwork(questionnaire);
    }
    this.loadCompleteness(questionnaire);
    this.reloadSelectedQuestion();
  }

  loadVariableOptions(variable: string | null): void {
    const questionnaire = this.questionnaire();
    this.variableOptions.set([]);
    if (!variable || !questionnaire) {
      return;
    }
    this.api.questionOptions(questionnaire.questionnaireId, questionnaire.version, variable).subscribe({
      next: (options) => this.variableOptions.set(options),
      error: () => this.variableOptions.set([])
    });
  }

  private loadFilterOptions(questionnaire: Questionnaire): void {
    this.api.filterOptions(questionnaire.questionnaireId, questionnaire.version).subscribe({
      next: (options) => {
        if (this.isCurrent(questionnaire)) {
          this.filterOptions.set(options);
        }
      },
      error: () => this.filterOptions.set(null)
    });
  }

  // ---- Suivi terrain -----------------------------------------------------------------

  reloadFieldwork(): void {
    const questionnaire = this.questionnaire();
    if (questionnaire) {
      this.loadFieldwork(questionnaire);
    }
  }

  /** Rafraîchit les indicateurs après une action de revue répercutée dans l'entrepôt local. */
  refreshAfterInterviewAction(): void {
    const questionnaire = this.questionnaire();
    if (!questionnaire) {
      return;
    }
    this.loadFieldwork(questionnaire);
    this.loadCompleteness(questionnaire);
    this.reloadSelectedQuestion();
  }

  private loadFieldwork(questionnaire: Questionnaire): void {
    this.fieldwork.set(null);
    this.fieldworkError.set(null);
    this.fieldworkLoading = true;
    this.api.fieldwork(questionnaire.questionnaireId, questionnaire.version, this.filters()).subscribe({
      next: (overview) => {
        this.fieldworkLoading = false;
        if (this.isCurrent(questionnaire)) {
          this.fieldwork.set(overview);
        }
      },
      error: (error: unknown) => {
        this.fieldworkLoading = false;
        if (!this.handleSyncPending(error, questionnaire)) {
          this.fieldworkError.set(apiErrorMessage(error, 'Impossible de calculer le suivi terrain.'));
        }
      }
    });
  }

  private fieldworkLead(): string {
    const overview = this.fieldwork();
    if (!overview) {
      return 'Avancement de la collecte par rapport aux affectations';
    }
    const day = (value: string) =>
      new Date(`${value}T00:00:00Z`).toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', timeZone: 'UTC' });
    const days = overview.daily.filter((item) => item.completed > 0);
    const interviewers = overview.teams.reduce((sum, team) => sum + team.interviewers.length, 0);
    const period = days.length === 0 ? 'Aucune interview terminée'
      : days.length === 1 ? `Collecte du ${day(days[0].date)}`
      : `Collecte du ${day(days[0].date)} au ${day(days[days.length - 1].date)}`;
    return `${period} · ${overview.teams.length} équipe(s) · ${interviewers} enquêteur(s)`;
  }

  // ---- Variables et qualité ----------------------------------------------------------

  reloadQuestions(): void {
    const questionnaire = this.questionnaire();
    if (questionnaire) {
      this.loadQuestions(questionnaire);
    }
  }

  reloadCompleteness(): void {
    const questionnaire = this.questionnaire();
    if (questionnaire) {
      this.loadCompleteness(questionnaire);
    }
  }

  completenessFor(question: QuestionSummary): QuestionCompleteness | null {
    return this.completeness()?.variables.find((item) => item.question.variableName === question.variableName) ?? null;
  }

  /** Sélectionne une variable par son nom (depuis l'onglet Qualité). Renvoie false si inconnue. */
  selectVariable(variableName: string): boolean {
    const question = this.questions().find((item) => item.variableName === variableName);
    if (question) {
      this.selectQuestion(question);
    }
    return !!question;
  }

  selectQuestion(question: QuestionSummary): void {
    const questionnaire = this.questionnaire();
    if (!questionnaire) {
      return;
    }
    this.selectedQuestion.set(question);
    this.analytics.set(null);
    this.analyticsError.set(null);
    this.analyticsLoading.set(true);
    this.api.questionAnalytics(questionnaire.questionnaireId, questionnaire.version, question.variableName, this.filters())
      .subscribe({
        next: (analytics) => {
          this.analyticsLoading.set(false);
          if (this.isCurrent(questionnaire) && this.selectedQuestion() === question) {
            this.analytics.set(analytics);
          }
        },
        error: (error: unknown) => {
          this.analyticsLoading.set(false);
          if (!this.handleSyncPending(error, questionnaire)) {
            this.analyticsError.set(apiErrorMessage(error, 'Impossible de calculer les indicateurs de cette variable.'));
          }
        }
      });
  }

  private reloadSelectedQuestion(): void {
    const question = this.selectedQuestion();
    if (question) {
      this.selectQuestion(question);
    }
  }

  private loadQuestions(questionnaire: Questionnaire): void {
    this.questionsLoading.set(true);
    this.questionsError.set(null);
    this.api.questions(questionnaire.questionnaireId, questionnaire.version).subscribe({
      next: (questions) => {
        if (!this.isCurrent(questionnaire)) {
          return;
        }
        this.questions.set(questions);
        this.questionsLoading.set(false);
        this.loadCompleteness(questionnaire);
        if (questions.length > 0) {
          this.selectQuestion(questions[0]);
        }
      },
      error: (error: unknown) => {
        this.questionsLoading.set(false);
        this.questionsError.set(apiErrorMessage(error, 'Impossible de charger les questions de ce questionnaire.'));
      }
    });
  }

  private loadCompleteness(questionnaire: Questionnaire): void {
    this.completeness.set(null);
    this.completenessError.set(null);
    this.api.questionCompleteness(questionnaire.questionnaireId, questionnaire.version, this.filters()).subscribe({
      next: (overview) => {
        if (this.isCurrent(questionnaire)) {
          this.completeness.set(overview);
        }
      },
      error: (error: unknown) => {
        if (!this.handleSyncPending(error, questionnaire)) {
          this.completenessError.set(apiErrorMessage(error, 'Impossible de calculer la complétude des variables.'));
        }
      }
    });
  }

  // ---- Rapports et exports -----------------------------------------------------------

  downloadExport(format: 'csv' | 'xlsx'): void {
    const questionnaire = this.questionnaire();
    if (!questionnaire || this.exporting()) {
      return;
    }
    this.exporting.set(format);
    this.downloadError.set(null);
    this.api.exportData(questionnaire.questionnaireId, questionnaire.version, format).subscribe({
      next: (file) => {
        this.exporting.set(null);
        saveFile(file, `survey-solutions-${questionnaire.questionnaireId}-v${questionnaire.version}.${format}`);
      },
      error: (error: unknown) => {
        this.exporting.set(null);
        this.downloadError.set('Export impossible. Vérifiez la connexion à HQ et le volume des données.');
        void blobErrorDetail(error).then((detail) => detail && this.downloadError.set(detail));
      }
    });
  }

  /** Rapport PDF du questionnaire, avec les filtres appliqués. */
  downloadReport(): void {
    const questionnaire = this.questionnaire();
    if (!questionnaire || this.reportGenerating()) {
      return;
    }
    this.reportGenerating.set(true);
    this.downloadError.set(null);
    this.api.report(questionnaire.questionnaireId, questionnaire.version, this.filters()).subscribe({
      next: (file) => {
        this.reportGenerating.set(false);
        saveFile(file, `rapport-${questionnaire.variable || 'questionnaire'}-v${questionnaire.version}.pdf`);
      },
      error: (error: unknown) => {
        this.reportGenerating.set(false);
        this.downloadError.set('Rapport PDF impossible. Vérifiez que les données sont synchronisées.');
        void blobErrorDetail(error).then((detail) => detail && this.downloadError.set(detail));
      }
    });
  }

  downloadArchivedReport(report: ArchivedReport): void {
    this.api.archivedReport(report.id).subscribe({
      next: (file) => saveFile(file, report.filename),
      error: () => this.downloadError.set('Ce rapport archivé n’a pas pu être téléchargé.')
    });
  }

  private loadArchivedReports(questionnaire: Questionnaire): void {
    this.api.archivedReports(questionnaire.questionnaireId, questionnaire.version).subscribe({
      next: (reports) => {
        if (this.isCurrent(questionnaire)) {
          this.archivedReports.set(reports);
        }
      },
      error: () => this.archivedReports.set([])
    });
  }

  private isCurrent(questionnaire: Questionnaire): boolean {
    const current = this.questionnaire();
    return !!current && current.questionnaireId === questionnaire.questionnaireId
      && current.version === questionnaire.version;
  }
}
