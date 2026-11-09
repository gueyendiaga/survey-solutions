import { CommonModule, DatePipe, DecimalPipe, registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, ViewEncapsulation, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DashboardChartComponent } from './dashboard-chart';
import { FieldworkPanelComponent } from './fieldwork-panel';
import { FilterBarComponent } from './filter-bar';
import { DashboardApiService } from './dashboard-api.service';
import {
  AnalysisFilters,
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

registerLocaleData(localeFr);

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
  protected readonly view = signal<'dashboard' | 'questionnaires' | 'questions' | 'fieldwork'>('dashboard');
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
  protected readonly statusLabelFn = (status: string): string => this.statusLabel(status);

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

  protected showFieldwork(event: Event): void {
    event.preventDefault();
    const questionnaire = this.currentQuestionnaire();
    if (!questionnaire) {
      this.view.set('questionnaires');
      return;
    }
    this.view.set('fieldwork');
    if (!this.fieldwork()) {
      this.loadFieldwork(questionnaire);
    }
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
    this.api.fieldwork(questionnaire.questionnaireId, questionnaire.version, this.filters()).subscribe({
      next: (overview) => {
        if (this.currentQuestionnaire() === questionnaire) {
          this.fieldwork.set(overview);
        }
      },
      error: (error: unknown) => {
        if (this.handleSyncPending(error, questionnaire)) {
          return;
        }
        this.fieldworkError.set(this.apiErrorMessage(error, 'Impossible de calculer le suivi terrain.'));
      }
    });
  }

  protected showQuestions(event: Event): void {
    event.preventDefault();
    if (this.currentQuestionnaire()) {
      this.view.set('questions');
    } else {
      this.view.set('questionnaires');
    }
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

  protected completenessFor(question: QuestionSummary): QuestionCompleteness | null {
    return this.completeness()?.variables.find(
      (item) => item.question.variableName === question.variableName
    ) ?? null;
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

  protected openQuestionnaire(questionnaire: Questionnaire): void {
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
    this.loadSyncStatus(questionnaire);
    this.loadFilterOptions(questionnaire);
    if (this.view() === 'fieldwork') {
      this.loadFieldwork(questionnaire);
    } else {
      this.view.set('questions');
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
        tooltip: {
          trigger: 'item' as const,
          formatter: (item: { name: string; value: number; percent: number }) =>
            `${item.name}<br/><strong>${item.value.toLocaleString('fr-FR')}</strong> réponse(s) · ${item.percent}%`
        },
        series: [
          {
            type: 'pie' as const,
            radius: '68%',
            data: analytics.distribution.map((item) => ({
              name: item.label,
              value: item.count
            })),
            label: {
              color: '#526176',
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
        grid: { left: 110, right: 24, top: 20, bottom: 32, containLabel: true },
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
          axisLabel: { color: '#8190a5' },
          splitLine: { lineStyle: { color: '#edf1f6' } }
        },
        yAxis: {
          type: 'category' as const,
          data: analytics.distribution.map((item) => item.label),
          axisLabel: { color: '#8190a5', width: 90, overflow: 'truncate' as const },
          axisLine: { lineStyle: { color: '#dce3ec' } },
          axisTick: { show: false }
        },
        series: [
          {
            type: 'bar' as const,
            data: analytics.distribution.map((item) => item.count),
            barMaxWidth: 40,
            itemStyle: { color: '#2563eb', borderRadius: [0, 5, 5, 0] }
          }
        ]
      };
    }

    return {
      animation: false,
      grid: { left: 48, right: 24, top: 20, bottom: 76, containLabel: true },
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
          color: '#8190a5',
          rotate: analytics.distribution.some((item) => item.label.length > 12) ? 25 : 0,
          interval: 0,
          hideOverlap: true
        },
        axisLine: { lineStyle: { color: '#dce3ec' } },
        axisTick: { show: false }
      },
      yAxis: {
        type: 'value' as const,
        minInterval: 1,
        axisLabel: { color: '#8190a5' },
        splitLine: { lineStyle: { color: '#edf1f6' } }
      },
      series: [
        {
          type: 'bar' as const,
          data: analytics.distribution.map((item) => item.count),
          barMaxWidth: 40,
          itemStyle: { color: '#2563eb', borderRadius: [5, 5, 0, 0] }
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
      axisLabel: { color: '#8190a5' },
      splitLine: { lineStyle: { color: '#edf1f6' } }
    },
    yAxis: {
      type: 'category' as const,
      data: dashboard.statuses.map((item) => this.statusLabel(item.status)),
      axisLabel: { color: '#536278', width: 170, overflow: 'truncate' as const },
      axisLine: { show: false },
      axisTick: { show: false }
    },
    series: [
      {
        type: 'bar' as const,
        data: dashboard.statuses.map((item) => item.count),
        barMaxWidth: 18,
        itemStyle: { color: '#2563eb', borderRadius: [0, 5, 5, 0] },
        emphasis: { itemStyle: { color: '#1d4ed8' } }
      }
    ]
  });

  protected readonly trendOptions = (dashboard: Dashboard) => ({
    animation: false,
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
      axisLabel: { color: '#8190a5', hideOverlap: true },
      axisLine: { lineStyle: { color: '#dce3ec' } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value' as const,
      minInterval: 1,
      axisLabel: { color: '#8190a5' },
      splitLine: { lineStyle: { color: '#edf1f6' } }
    },
    series: [
      {
        type: 'line' as const,
        data: dashboard.updatesByDay.map((item) => item.count),
        smooth: 0.28,
        symbolSize: 7,
        lineStyle: { color: '#2563eb', width: 3 },
        itemStyle: { color: '#2563eb', borderColor: '#ffffff', borderWidth: 2 },
        areaStyle: { color: 'rgba(37, 99, 235, 0.10)' }
      }
    ]
  });
}
