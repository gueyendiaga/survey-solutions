import { CommonModule, DatePipe, DecimalPipe, registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, ViewEncapsulation, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DashboardChartComponent } from './dashboard-chart';
import { FieldworkPanelComponent } from './fieldwork-panel';
import { FilterBarComponent } from './filter-bar';
import { DashboardApiService } from './dashboard-api.service';
import {
  AnalysisFilters,
  ArchivedReport,
  CompletenessOverview,
  FieldworkOverview,
  FilterOptions,
  NO_FILTERS,
  QuestionOption,
  Dashboard,
  QuestionAnalytics,
  QuestionCompleteness,
  Questionnaire,
  QuestionSummary,
  SyncStatus
} from './dashboard.models';

const SYNC_POLL_INTERVAL_MS = 3000;

/** Écrans généraux, puis écrans du questionnaire ouvert. */
export type View = 'dashboard' | 'questionnaires' | QuestionnaireView;
export type QuestionnaireView = 'fieldwork' | 'variables' | 'quality' | 'reports';

const VIEW_TITLES: Record<View, string> = {
  dashboard: 'Tableau de bord',
  questionnaires: 'Questionnaires',
  fieldwork: 'Suivi terrain',
  variables: 'Variables',
  quality: 'Qualité des données',
  reports: 'Rapports'
};

export type VariableKind = 'choice' | 'numeric' | 'text' | 'other';

const VARIABLE_KIND_LABELS: Record<VariableKind, string> = {
  choice: 'Choix',
  numeric: 'Numériques',
  text: 'Texte',
  other: 'Autres'
};

export function variableKind(question: QuestionSummary): VariableKind {
  const type = (question.type ?? '').toLowerCase();
  if (type.includes('single') || type.includes('multi') || type.includes('multy')) {
    return 'choice';
  }
  if (type.includes('numeric')) {
    return 'numeric';
  }
  return type.includes('text') ? 'text' : 'other';
}

/** Palette des graphiques : bleu encre pour les données, ocre en second. */
export const CHART_COLORS = ['#3a669e', '#6f93c2', '#c27c1e', '#9db6d6', '#2d5283', '#e0a85a', '#1f6b45', '#8a4f0c'];

registerLocaleData(localeFr);

/** Coupe un libellé en lignes de lineLength caractères au plus, avec « … » au-delà de maxLines. */
export function shortLabel(label: string, lineLength: number, maxLines: number): string {
  const words = label.trim().split(/\s+/);
  const lines: string[] = [];
  let current = '';
  for (const word of words) {
    const candidate = current ? `${current} ${word}` : word;
    if (candidate.length <= lineLength) {
      current = candidate;
      continue;
    }
    if (current) {
      lines.push(current);
    }
    current = word.length > lineLength ? `${word.slice(0, lineLength - 1)}…` : word;
  }
  if (current) {
    lines.push(current);
  }
  if (lines.length <= maxLines) {
    return lines.join('\n');
  }
  const kept = lines.slice(0, maxLines);
  const last = kept[maxLines - 1];
  kept[maxLines - 1] = `${last.length >= lineLength ? last.slice(0, lineLength - 1) : last}…`;
  return kept.join('\n');
}

@Component({
  selector: 'app-root',
  imports: [
    CommonModule,
    DatePipe,
    DecimalPipe,
    FormsModule,
    DashboardChartComponent,
    FieldworkPanelComponent,
    FilterBarComponent
  ],
  templateUrl: './app.html',
  styleUrl: './app.css',
  // Feuille de style commune à l'application : les composants enfants (suivi terrain, filtres)
  // réutilisent ses classes (panel, kpi-card, distribution-table…).
  encapsulation: ViewEncapsulation.None
})
export class App implements OnInit, OnDestroy {
  protected readonly questionnaires = signal<Questionnaire[]>([]);
  protected readonly dashboard = signal<Dashboard | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly selectedKey = signal('');
  protected readonly view = signal<View>('dashboard');
  protected readonly currentQuestionnaire = signal<Questionnaire | null>(null);
  protected readonly questions = signal<QuestionSummary[]>([]);
  protected readonly selectedQuestion = signal<QuestionSummary | null>(null);
  protected readonly questionAnalytics = signal<QuestionAnalytics | null>(null);
  protected readonly answerChartType = signal<'bar' | 'horizontalBar' | 'pie'>('bar');
  protected readonly completeness = signal<CompletenessOverview | null>(null);
  protected readonly completenessLoading = signal(false);
  protected readonly completenessError = signal<string | null>(null);
  protected readonly exporting = signal<'csv' | 'xlsx' | null>(null);
  protected readonly exportError = signal<string | null>(null);
  protected readonly syncStatus = signal<SyncStatus | null>(null);
  protected readonly syncRequesting = signal(false);
  private syncPollTimer: ReturnType<typeof setTimeout> | null = null;
  protected readonly filters = signal<AnalysisFilters>(NO_FILTERS);
  protected readonly filterOptions = signal<FilterOptions | null>(null);
  protected readonly variableOptions = signal<QuestionOption[]>([]);
  protected readonly fieldwork = signal<FieldworkOverview | null>(null);
  protected readonly fieldworkError = signal<string | null>(null);
  private fieldworkLoading = false;
  protected readonly reportGenerating = signal(false);
  protected readonly archivedReports = signal<ArchivedReport[]>([]);
  protected readonly statusLabelFn = (status: string): string => this.statusLabel(status);
  protected readonly exportMenuOpen = signal(false);
  protected readonly variableSearch = signal('');
  protected readonly variableType = signal<VariableKind | 'all'>('all');
  protected readonly filteredQuestions = computed(() => {
    const term = this.variableSearch().trim().toLowerCase();
    const kind = this.variableType();
    return this.questions().filter((question) =>
      (kind === 'all' || variableKind(question) === kind)
      && (!term || `${question.variableName} ${question.label} ${question.questionText}`.toLowerCase().includes(term)));
  });
  /** Filtres par type proposés au-dessus de la liste (seuls les types présents). */
  protected readonly variableKinds = computed(() => {
    const counts = new Map<VariableKind, number>();
    this.questions().forEach((question) => counts.set(variableKind(question), (counts.get(variableKind(question)) ?? 0) + 1));
    return (['choice', 'numeric', 'text', 'other'] as VariableKind[])
      .filter((kind) => counts.has(kind))
      .map((kind) => ({ kind, label: VARIABLE_KIND_LABELS[kind], count: counts.get(kind) ?? 0 }));
  });
  protected readonly viewTitles = VIEW_TITLES;

  /** Variables triées du taux de réponse le plus faible au plus élevé (écran Qualité). */
  protected readonly qualityRows = computed(() =>
    [...(this.completeness()?.variables ?? [])].sort((a, b) => {
      const posedA = a.answeredCount + a.missingCount > 0;
      const posedB = b.answeredCount + b.missingCount > 0;
      if (posedA !== posedB) {
        return posedA ? -1 : 1;
      }
      return a.responseRate - b.responseRate || a.question.variableName.localeCompare(b.question.variableName);
    })
  );

  protected readonly qualitySummary = computed(() => {
    const rows = this.completeness()?.variables ?? [];
    const posed = rows.filter((row) => row.answeredCount + row.missingCount > 0);
    const average = posed.length ? posed.reduce((sum, row) => sum + row.responseRate, 0) / posed.length : null;
    return {
      variables: rows.length,
      posed: posed.length,
      average,
      low: posed.filter((row) => row.responseRate < 80).length,
      missing: rows.reduce((sum, row) => sum + row.missingCount, 0)
    };
  });

  constructor(private readonly api: DashboardApiService) {}

  ngOnInit(): void {
    this.refresh();
  }

  ngOnDestroy(): void {
    this.stopSyncPolling();
  }

  /** Synchronisation manuelle : export HQ puis import, suivi jusqu'à la fin. */
  protected startSync(): void {
    const questionnaire = this.currentQuestionnaire();
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
        this.error.set(this.apiErrorMessage(error, 'Impossible de lancer la synchronisation.'));
      }
    });
  }

  private loadSyncStatus(questionnaire: Questionnaire): void {
    this.api.syncStatus(questionnaire.questionnaireId, questionnaire.version).subscribe({
      next: (status) => {
        if (this.currentQuestionnaire() !== questionnaire) {
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
    if (this.currentQuestionnaire() !== questionnaire) {
      return;
    }
    const previousSuccess = this.syncStatus()?.lastSuccessAt ?? null;
    this.api.syncStatus(questionnaire.questionnaireId, questionnaire.version).subscribe({
      next: (status) => {
        if (this.currentQuestionnaire() !== questionnaire) {
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

  private reloadAnalyses(questionnaire: Questionnaire): void {
    this.loadFilterOptions(questionnaire);
    if (this.view() === 'fieldwork') {
      this.loadFieldwork(questionnaire);
    } else {
      this.fieldwork.set(null);
    }
    this.loadCompleteness(questionnaire);
    const question = this.selectedQuestion();
    if (question) {
      this.selectQuestion(question);
    }
  }

  private stopSyncPolling(): void {
    if (this.syncPollTimer !== null) {
      clearTimeout(this.syncPollTimer);
      this.syncPollTimer = null;
    }
  }

  /** 409 + syncPending : les données sont en cours d'import, on attend la fin. */
  private handleSyncPending(error: unknown, questionnaire: Questionnaire): boolean {
    if (!(error instanceof HttpErrorResponse) || error.status !== 409 || !error.error?.syncPending) {
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

  protected refresh(): void {
    this.loading.set(true);
    this.error.set(null);
    this.dashboard.set(null);
    const selectedKey = this.selectedKey();
    const selected = this.questionnaires().find(
      (item) => `${item.questionnaireId}$${item.version}` === selectedKey
    );
    this.api.dashboard(selected?.questionnaireId, selected?.version).subscribe({
      next: (dashboard) => {
        this.dashboard.set(dashboard);
        this.questionnaires.set(dashboard.questionnaires);
        this.selectedKey.set(
          dashboard.questionnaires.some(
            (item) => `${item.questionnaireId}$${item.version}` === selectedKey
          )
            ? selectedKey
            : ''
        );
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set(
          'Impossible de charger les données. Vérifiez la connexion à HQ et les permissions du compte API.'
        );
      }
    });
  }

  protected selectQuestionnaire(key: string): void {
    this.selectedKey.set(key);
    if (!key) {
      this.refresh();
      return;
    }

    const questionnaire = this.questionnaires().find(
      (item) => `${item.questionnaireId}$${item.version}` === key
    );
    if (!questionnaire) {
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.dashboard.set(null);
    this.api.dashboard(questionnaire.questionnaireId, questionnaire.version).subscribe({
      next: (dashboard) => {
        this.dashboard.set(dashboard);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Impossible de charger les indicateurs de ce questionnaire.');
      }
    });
  }

  protected showQuestionnaires(event: Event): void {
    event.preventDefault();
    this.view.set('questionnaires');
  }

  /** Navigation entre les écrans du questionnaire ouvert. */
  protected showQuestionnaireView(target: QuestionnaireView, event?: Event): void {
    event?.preventDefault();
    this.exportMenuOpen.set(false);
    const questionnaire = this.currentQuestionnaire();
    if (!questionnaire) {
      this.view.set('questionnaires');
      return;
    }
    this.view.set(target);
    if (target === 'fieldwork' && !this.fieldwork() && !this.fieldworkLoading) {
      this.loadFieldwork(questionnaire);
    }
    if (target === 'reports') {
      this.loadArchivedReports(questionnaire);
    }
  }

  protected showFieldwork(event: Event): void {
    this.showQuestionnaireView('fieldwork', event);
  }

  /** Depuis l'écran Qualité : ouvre la variable dans l'écran Variables. */
  protected openVariable(variableName: string): void {
    const question = this.questions().find((item) => item.variableName === variableName);
    if (question) {
      this.view.set('variables');
      this.selectQuestion(question);
    }
  }

  /** Sous-titre de l'écran du questionnaire ouvert, tiré des données chargées. */
  protected viewLead(): string {
    const day = (value: string) =>
      new Date(`${value}T00:00:00Z`).toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', timeZone: 'UTC' });
    switch (this.view()) {
      case 'fieldwork': {
        const overview = this.fieldwork();
        if (!overview) {
          return 'Avancement de la collecte par rapport aux affectations';
        }
        const days = overview.daily.filter((item) => item.completed > 0);
        const interviewers = overview.teams.reduce((sum, team) => sum + team.interviewers.length, 0);
        const period = days.length === 0 ? 'Aucune interview terminée'
          : days.length === 1 ? `Collecte du ${day(days[0].date)}`
          : `Collecte du ${day(days[0].date)} au ${day(days[days.length - 1].date)}`;
        return `${period} · ${overview.teams.length} équipe(s) · ${interviewers} enquêteur(s)`;
      }
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
  }

  /** Recherche de la barre supérieure : ouvre l'écran Variables filtré sur le terme saisi. */
  protected searchVariables(term: string): void {
    this.variableSearch.set(term);
    this.variableType.set('all');
    if (this.currentQuestionnaire() && this.view() !== 'variables') {
      this.showQuestionnaireView('variables');
    }
  }

  protected toggleExportMenu(): void {
    this.exportMenuOpen.update((open) => !open);
  }

  protected isQuestionnaireView(): boolean {
    const view = this.view();
    return view === 'fieldwork' || view === 'variables' || view === 'quality' || view === 'reports';
  }

  protected reloadFieldwork(): void {
    const questionnaire = this.currentQuestionnaire();
    if (questionnaire) {
      this.loadFieldwork(questionnaire);
    }
  }

  /** Applique les filtres au suivi terrain, à la complétude et à la question sélectionnée. */
  protected applyFilters(filters: AnalysisFilters): void {
    const questionnaire = this.currentQuestionnaire();
    this.filters.set(filters);
    if (!questionnaire) {
      return;
    }
    this.fieldwork.set(null);
    if (this.view() === 'fieldwork') {
      this.loadFieldwork(questionnaire);
    }
    this.loadCompleteness(questionnaire);
    const question = this.selectedQuestion();
    if (question) {
      this.selectQuestion(question);
    }
  }

  protected loadVariableOptions(variable: string | null): void {
    const questionnaire = this.currentQuestionnaire();
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
        if (this.currentQuestionnaire() === questionnaire) {
          this.filterOptions.set(options);
        }
      },
      error: () => this.filterOptions.set(null)
    });
  }

  private loadFieldwork(questionnaire: Questionnaire): void {
    this.fieldwork.set(null);
    this.fieldworkError.set(null);
    this.fieldworkLoading = true;
    this.api.fieldwork(questionnaire.questionnaireId, questionnaire.version, this.filters()).subscribe({
      next: (overview) => {
        this.fieldworkLoading = false;
        if (this.currentQuestionnaire() === questionnaire) {
          this.fieldwork.set(overview);
        }
      },
      error: (error: unknown) => {
        this.fieldworkLoading = false;
        if (this.handleSyncPending(error, questionnaire)) {
          return;
        }
        this.fieldworkError.set(this.apiErrorMessage(error, 'Impossible de calculer le suivi terrain.'));
      }
    });
  }

  protected showQuestions(event: Event): void {
    this.showQuestionnaireView('variables', event);
  }

  protected reloadQuestions(): void {
    const questionnaire = this.currentQuestionnaire();
    if (questionnaire) {
      this.openQuestionnaire(questionnaire);
    } else {
      this.refresh();
    }
  }

  protected reloadCompleteness(): void {
    const questionnaire = this.currentQuestionnaire();
    if (questionnaire) {
      this.loadCompleteness(questionnaire);
    }
  }

  protected downloadExport(format: 'csv' | 'xlsx'): void {
    const questionnaire = this.currentQuestionnaire();
    if (!questionnaire || this.exporting()) {
      return;
    }

    this.exporting.set(format);
    this.exportError.set(null);
    this.api
      .exportData(questionnaire.questionnaireId, questionnaire.version, format)
      .subscribe({
        next: (file) => {
          const url = URL.createObjectURL(file);
          const link = document.createElement('a');
          link.href = url;
          link.download = `survey-solutions-${questionnaire.questionnaireId}-v${questionnaire.version}.${format}`;
          link.click();
          URL.revokeObjectURL(url);
          this.exporting.set(null);
        },
        error: (error: unknown) => {
          this.exporting.set(null);
          this.exportError.set(
            'Export impossible. Vérifiez la connexion à HQ et le volume des données.'
          );
          if (error instanceof HttpErrorResponse && error.error instanceof Blob) {
            void error.error.text().then((body) => {
              const detail = this.problemDetail(body);
              if (detail) {
                this.exportError.set(detail);
              }
            });
          }
        }
      });
  }

  /** Rapport PDF du questionnaire, avec les filtres appliqués. */
  protected downloadReport(): void {
    const questionnaire = this.currentQuestionnaire();
    if (!questionnaire || this.reportGenerating()) {
      return;
    }
    this.reportGenerating.set(true);
    this.exportError.set(null);
    this.api.report(questionnaire.questionnaireId, questionnaire.version, this.filters()).subscribe({
      next: (file) => {
        this.reportGenerating.set(false);
        this.saveFile(file, `rapport-${questionnaire.variable || 'questionnaire'}-v${questionnaire.version}.pdf`);
      },
      error: (error: unknown) => {
        this.reportGenerating.set(false);
        this.exportError.set('Rapport PDF impossible. Vérifiez que les données sont synchronisées.');
        this.blobProblem(error);
      }
    });
  }

  protected downloadArchivedReport(report: ArchivedReport): void {
    this.api.archivedReport(report.id).subscribe({
      next: (file) => this.saveFile(file, report.filename),
      error: () => this.exportError.set('Ce rapport archivé n’a pas pu être téléchargé.')
    });
  }

  private loadArchivedReports(questionnaire: Questionnaire): void {
    this.api.archivedReports(questionnaire.questionnaireId, questionnaire.version).subscribe({
      next: (reports) => {
        if (this.currentQuestionnaire() === questionnaire) {
          this.archivedReports.set(reports);
        }
      },
      error: () => this.archivedReports.set([])
    });
  }

  private saveFile(file: Blob, filename: string): void {
    const url = URL.createObjectURL(file);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
  }

  private blobProblem(error: unknown): void {
    if (error instanceof HttpErrorResponse && error.error instanceof Blob) {
      void error.error.text().then((body) => {
        const detail = this.problemDetail(body);
        if (detail) {
          this.exportError.set(detail);
        }
      });
    }
  }

  protected completenessFor(question: QuestionSummary): QuestionCompleteness | null {
    return this.completeness()?.variables.find(
      (item) => item.question.variableName === question.variableName
    ) ?? null;
  }

  /** Type de question en français, avec le nombre de modalités pour les choix. */
  protected typeLabel(question: QuestionSummary): string {
    const type = (question.type ?? '').toLowerCase();
    const label = type.includes('multi') || type.includes('multy') ? 'Choix multiples'
      : type.includes('single') ? 'Choix unique'
      : type.includes('numeric') ? 'Numérique'
      : type.includes('date') ? 'Date'
      : type.includes('textlist') ? 'Liste de textes'
      : type.includes('text') ? 'Texte'
      : type.includes('gps') ? 'GPS'
      : type.includes('multimedia') ? 'Photo'
      : type.includes('audio') ? 'Audio'
      : type.includes('barcode') ? 'Code-barres'
      : question.type || 'Question';
    return question.optionCount ? `${label} · ${question.optionCount} modalités` : label;
  }

  protected isSensitiveQuestion(question: QuestionSummary): boolean {
    const type = (question.type ?? '').toLowerCase();
    return type.includes('text')
      || type.includes('multimedia')
      || type.includes('barcode')
      || type.includes('area')
      || type.includes('audio')
      || type.includes('gps')
      || type.includes('geopoint')
      || type.includes('coordinate')
      || type.includes('geospatial');
  }

  protected showDashboard(event: Event): void {
    event.preventDefault();
    this.view.set('dashboard');
  }

  protected openQuestionnaire(questionnaire: Questionnaire, target: QuestionnaireView = 'fieldwork'): void {
    const key = `${questionnaire.questionnaireId}$${questionnaire.version}`;
    this.selectedKey.set(key);
    this.currentQuestionnaire.set(questionnaire);
    this.selectedQuestion.set(null);
    this.questionAnalytics.set(null);
    this.completeness.set(null);
    this.completenessError.set(null);
    this.completenessLoading.set(false);
    this.questions.set([]);
    this.stopSyncPolling();
    this.syncStatus.set(null);
    this.filters.set(NO_FILTERS);
    this.filterOptions.set(null);
    this.variableOptions.set([]);
    this.fieldwork.set(null);
    this.fieldworkError.set(null);
    this.archivedReports.set([]);
    this.loadSyncStatus(questionnaire);
    this.loadFilterOptions(questionnaire);
    this.exportMenuOpen.set(false);
    this.view.set(target);
    if (target === 'fieldwork') {
      this.loadFieldwork(questionnaire);
    }
    if (target === 'reports') {
      this.loadArchivedReports(questionnaire);
    }
    this.loading.set(true);
    this.error.set(null);
    this.api.questions(questionnaire.questionnaireId, questionnaire.version).subscribe({
      next: (questions) => {
        this.questions.set(questions);
        this.loading.set(false);
        this.loadCompleteness(questionnaire);
        if (questions.length > 0) {
          this.selectQuestion(questions[0]);
        }
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.error.set(this.apiErrorMessage(
          error,
          'Impossible de charger les questions de ce questionnaire.'
        ));
      }
    });
  }

  private loadCompleteness(questionnaire: Questionnaire): void {
    this.completeness.set(null);
    this.completenessLoading.set(true);
    this.completenessError.set(null);
    this.api
      .questionCompleteness(questionnaire.questionnaireId, questionnaire.version, this.filters())
      .subscribe({
        next: (overview) => {
          this.completeness.set(overview);
          this.completenessLoading.set(false);
        },
        error: (error: unknown) => {
          this.completenessLoading.set(false);
          if (this.handleSyncPending(error, questionnaire)) {
            return;
          }
          this.completenessError.set(this.apiErrorMessage(
            error,
            'Impossible de calculer la complétude des variables.'
          ));
        }
      });
  }

  protected selectQuestion(question: QuestionSummary): void {
    const questionnaire = this.currentQuestionnaire();
    if (!questionnaire) {
      return;
    }
    this.selectedQuestion.set(question);
    this.questionAnalytics.set(null);
    this.loading.set(true);
    this.error.set(null);
    this.api
      .questionAnalytics(
        questionnaire.questionnaireId,
        questionnaire.version,
        question.variableName,
        this.filters()
      )
      .subscribe({
        next: (analytics) => {
          this.questionAnalytics.set(analytics);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          if (this.handleSyncPending(error, questionnaire)) {
            return;
          }
          this.error.set(this.apiErrorMessage(
            error,
            'Impossible de calculer les indicateurs de cette question.'
          ));
        }
      });
  }

  private apiErrorMessage(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse) {
      const detail = error.error?.detail;
      if (typeof detail === 'string') {
        return detail;
      }
    }
    return fallback;
  }

  private problemDetail(body: string): string | null {
    try {
      const problem: unknown = JSON.parse(body);
      if (problem !== null && typeof problem === 'object' && 'detail' in problem) {
        const detail = problem.detail;
        return typeof detail === 'string' ? detail : null;
      }
    } catch (error: unknown) {
      if (!(error instanceof SyntaxError)) {
        throw error;
      }
    }
    return null;
  }

  protected readonly answerOptions = (analytics: QuestionAnalytics) => {
    if (this.answerChartType() === 'pie') {
      return {
        animation: false,
        textStyle: { fontFamily: 'Inter, sans-serif' },
        tooltip: {
          trigger: 'item' as const,
          formatter: (item: { name: string; value: number; percent: number }) =>
            `${item.name}<br/><strong>${item.value.toLocaleString('fr-FR')}</strong> réponse(s) · ${item.percent}%`
        },
        series: [
          {
            type: 'pie' as const,
            color: CHART_COLORS,
            radius: '68%',
            data: analytics.distribution.map((item) => ({
              name: item.label,
              value: item.count
            })),
            label: {
              color: '#374151',
              formatter: '{b}\n{d}%'
            },
            itemStyle: { borderColor: '#fff', borderWidth: 2 }
          }
        ]
      };
    }

    if (this.answerChartType() === 'horizontalBar') {
      return {
        animation: false,
        textStyle: { fontFamily: 'Inter, sans-serif' },
        grid: { left: 16, right: 40, top: 12, bottom: 24, containLabel: true },
        tooltip: {
          trigger: 'axis' as const,
          axisPointer: { type: 'shadow' as const },
          formatter: (items: { dataIndex: number; value: number }[]) => {
            const answer = analytics.distribution[items[0].dataIndex];
            return `${answer.label}<br/><strong>${answer.count.toLocaleString('fr-FR')}</strong> réponse(s) · ${answer.percentage}%`;
          }
        },
        xAxis: {
          type: 'value' as const,
          minInterval: 1,
          axisLabel: { color: '#6b7280' },
          splitLine: { lineStyle: { color: '#f0f2f6' } }
        },
        yAxis: {
          type: 'category' as const,
          data: analytics.distribution.map((item) => item.label),
          axisLabel: { color: '#374151', width: 160, overflow: 'truncate' as const },
          inverse: true,
          axisLine: { lineStyle: { color: '#e6eaf0' } },
          axisTick: { show: false }
        },
        series: [
          {
            type: 'bar' as const,
            data: analytics.distribution.map((item) => item.count),
            barMaxWidth: 40,
            itemStyle: { color: '#3a669e', borderRadius: [0, 6, 6, 0] }
          }
        ]
      };
    }

    // Libellés courts sous les barres : les modalités longues (phrases entières) écrasaient
    // sinon la zone de tracé. Le libellé complet reste dans l'infobulle et le tableau.
    const categoryCount = analytics.distribution.length;
    const rotateLabels = categoryCount > 6;
    const lineLength = rotateLabels ? 14 : categoryCount <= 3 ? 30 : categoryCount <= 5 ? 20 : 14;
    return {
      animation: false,
      textStyle: { fontFamily: 'Inter, sans-serif' },
      grid: { left: 48, right: 24, top: 20, bottom: 12, containLabel: true },
      tooltip: {
        trigger: 'axis' as const,
        axisPointer: { type: 'shadow' as const },
        formatter: (items: { dataIndex: number; value: number }[]) => {
          const answer = analytics.distribution[items[0].dataIndex];
          return `${answer.label}<br/><strong>${answer.count.toLocaleString('fr-FR')}</strong> réponse(s) · ${answer.percentage}%`;
        }
      },
      xAxis: {
        type: 'category' as const,
        data: analytics.distribution.map((item) => item.label),
        axisLabel: {
          color: '#6b7280',
          rotate: rotateLabels ? 30 : 0,
          interval: 0,
          lineHeight: 14,
          formatter: (label: string) => shortLabel(label, lineLength, rotateLabels ? 1 : 2)
        },
        axisLine: { lineStyle: { color: '#e6eaf0' } },
        axisTick: { show: false }
      },
      yAxis: {
        type: 'value' as const,
        minInterval: 1,
        axisLabel: { color: '#6b7280' },
        splitLine: { lineStyle: { color: '#f0f2f6' } }
      },
      series: [
        {
          type: 'bar' as const,
          data: analytics.distribution.map((item) => item.count),
          barMaxWidth: 40,
          itemStyle: { color: '#3a669e', borderRadius: [6, 6, 0, 0] }
        }
      ]
    };
  };

  protected statusLabel(status: string): string {
    const labels: Record<string, string> = {
      Restored: 'Restaurées',
      Created: 'Créées',
      SupervisorAssigned: 'Affectées au superviseur',
      InterviewerAssigned: 'Affectées à l’enquêteur',
      RejectedBySupervisor: 'Rejetées par le superviseur',
      ReadyForInterview: 'Prêtes pour entretien',
      SentToCapi: 'Envoyées vers CAPI',
      Restarted: 'Redémarrées',
      Completed: 'Terminées',
      ApprovedBySupervisor: 'Approuvées par le superviseur',
      RejectedByHeadquarters: 'Rejetées par HQ',
      ApprovedByHeadquarters: 'Approuvées par HQ',
      Deleted: 'Supprimées',
      Inconnu: 'Statut inconnu'
    };
    return labels[status] ?? status;
  }

  /** Unité d'observation : interviews au niveau principal, lignes pour un roster. */
  protected unitLabel(level: string | null): string {
    const questionnaire = this.currentQuestionnaire();
    if (!level || !questionnaire || level.toLowerCase() === questionnaire.variable?.toLowerCase()) {
      return 'Interviews';
    }
    return `Lignes ${level}`;
  }

  protected hasSpecialValues(analytics: QuestionAnalytics): boolean {
    return (analytics.specialValues ?? []).length > 0;
  }

  protected statusList(statuses: string[] | null | undefined): string {
    return (statuses ?? []).map((status) => this.statusLabel(status)).join(', ');
  }

  protected readonly statusOptions = (dashboard: Dashboard) => ({
    animation: false,
    textStyle: { fontFamily: 'Inter, sans-serif' },
    grid: { left: 190, right: 24, top: 16, bottom: 24 },
    tooltip: {
      trigger: 'axis' as const,
      axisPointer: { type: 'shadow' as const },
      formatter: (items: { dataIndex: number; value: number }[]) => {
        const item = items[0];
        const status = dashboard.statuses[item.dataIndex];
        return `${this.statusLabel(status.status)}<br/><strong>${item.value.toLocaleString('fr-FR')}</strong> interview(s)`;
      }
    },
    xAxis: {
      type: 'value' as const,
      minInterval: 1,
      axisLabel: { color: '#6b7280' },
      splitLine: { lineStyle: { color: '#f0f2f6' } }
    },
    yAxis: {
      type: 'category' as const,
      data: dashboard.statuses.map((item) => this.statusLabel(item.status)),
      axisLabel: { color: '#374151', width: 170, overflow: 'truncate' as const },
      axisLine: { show: false },
      axisTick: { show: false }
    },
    series: [
      {
        type: 'bar' as const,
        data: dashboard.statuses.map((item) => item.count),
        barMaxWidth: 18,
        itemStyle: { color: '#3a669e', borderRadius: [0, 5, 5, 0] },
        emphasis: { itemStyle: { color: '#2d5283' } }
      }
    ]
  });

  protected readonly trendOptions = (dashboard: Dashboard) => ({
    animation: false,
    textStyle: { fontFamily: 'Inter, sans-serif' },
    grid: { left: 42, right: 22, top: 20, bottom: 34 },
    tooltip: { trigger: 'axis' as const },
    xAxis: {
      type: 'category' as const,
      boundaryGap: false,
      data: dashboard.updatesByDay.map((item) =>
        new Date(`${item.date}T00:00:00`).toLocaleDateString('fr-FR', {
          day: '2-digit',
          month: 'short',
          timeZone: 'UTC'
        })
      ),
      axisLabel: { color: '#6b7280', hideOverlap: true },
      axisLine: { lineStyle: { color: '#e6eaf0' } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value' as const,
      minInterval: 1,
      axisLabel: { color: '#6b7280' },
      splitLine: { lineStyle: { color: '#f0f2f6' } }
    },
    series: [
      {
        type: 'line' as const,
        data: dashboard.updatesByDay.map((item) => item.count),
        smooth: 0.28,
        symbolSize: 7,
        lineStyle: { color: '#3a669e', width: 3 },
        itemStyle: { color: '#3a669e', borderColor: '#ffffff', borderWidth: 2 },
        areaStyle: { color: 'rgba(58, 102, 158, 0.12)' }
      }
    ]
  });
}
