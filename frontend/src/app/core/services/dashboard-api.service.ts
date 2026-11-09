import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import {
  AnalysisFilters,
  ArchivedReport,
  CompletenessOverview,
  FieldworkOverview,
  FilterOptions,
  QuestionOption,
  Dashboard,
  QuestionAnalytics,
  Questionnaire,
  QuestionSummary,
  SyncStatus
} from '../models/dashboard.models';

function normalizeKeys(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(normalizeKeys);
  }
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([key, child]) => [
        key.charAt(0).toLowerCase() + key.slice(1),
        normalizeKeys(child)
      ])
    );
  }
  return value;
}

/** Statuts envoyés pour le choix « tous les statuts » de la barre de filtres. */
const ALL_STATUSES = [
  'SupervisorAssigned', 'InterviewerAssigned', 'Restarted', 'Completed', 'RejectedBySupervisor',
  'ApprovedBySupervisor', 'RejectedByHeadquarters', 'ApprovedByHeadquarters'
];

export function filterParams(filters: AnalysisFilters, base = new HttpParams()): HttpParams {
  let params = base;
  const simple: (keyof AnalysisFilters)[] = ['from', 'to', 'supervisor', 'interviewer', 'variable', 'value'];
  for (const key of simple) {
    const value = filters[key];
    if (value) {
      params = params.set(key, value);
    }
  }
  if (filters.status === 'ALL') {
    params = params.set('statuses', ALL_STATUSES.join(','));
  } else if (filters.status) {
    params = params.set('statuses', filters.status);
  }
  return params;
}

@Injectable({ providedIn: 'root' })
export class DashboardApiService {
  private readonly baseUrl = '/api';

  constructor(private readonly http: HttpClient) {}

  questionnaires(): Observable<Questionnaire[]> {
    return this.http
      .get<unknown>(`${this.baseUrl}/questionnaires`)
      .pipe(map((response) => normalizeKeys(response) as Questionnaire[]));
  }

  dashboard(questionnaireId?: string, version?: number): Observable<Dashboard> {
    let params = new HttpParams();
    if (questionnaireId) {
      params = params.set('questionnaireId', questionnaireId);
    }
    if (version !== undefined) {
      params = params.set('version', version);
    }
    return this.http
      .get<unknown>(`${this.baseUrl}/dashboard`, { params })
      .pipe(map((response) => normalizeKeys(response) as Dashboard));
  }

  questions(questionnaireId: string, version: number): Observable<QuestionSummary[]> {
    return this.http
      .get<unknown>(`${this.baseUrl}/questionnaires/${questionnaireId}/${version}/question-list`)
      .pipe(map((response) => normalizeKeys(response) as QuestionSummary[]));
  }

  questionAnalytics(
    questionnaireId: string,
    version: number,
    variableName: string,
    filters: AnalysisFilters
  ): Observable<QuestionAnalytics> {
    const params = filterParams(filters, new HttpParams().set('variableName', variableName));
    return this.http
      .get<unknown>(
        `${this.baseUrl}/questionnaires/${questionnaireId}/${version}/question-analytics`,
        { params }
      )
      .pipe(map((response) => normalizeKeys(response) as QuestionAnalytics));
  }

  questionCompleteness(
    questionnaireId: string,
    version: number,
    filters: AnalysisFilters
  ): Observable<CompletenessOverview> {
    return this.http
      .get<unknown>(
        `${this.baseUrl}/questionnaires/${questionnaireId}/${version}/question-completeness`,
        { params: filterParams(filters) }
      )
      .pipe(map((response) => normalizeKeys(response) as CompletenessOverview));
  }

  fieldwork(
    questionnaireId: string,
    version: number,
    filters: AnalysisFilters
  ): Observable<FieldworkOverview> {
    return this.http
      .get<unknown>(`${this.baseUrl}/questionnaires/${questionnaireId}/${version}/fieldwork`, {
        params: filterParams(filters)
      })
      .pipe(map((response) => normalizeKeys(response) as FieldworkOverview));
  }

  filterOptions(questionnaireId: string, version: number): Observable<FilterOptions> {
    return this.http
      .get<unknown>(`${this.baseUrl}/questionnaires/${questionnaireId}/${version}/filter-options`)
      .pipe(map((response) => normalizeKeys(response) as FilterOptions));
  }

  /** Modalités d'une variable à choix unique (codes et libellés du questionnaire HQ). */
  questionOptions(
    questionnaireId: string,
    version: number,
    variableName: string
  ): Observable<QuestionOption[]> {
    return this.http
      .get<unknown>(`${this.baseUrl}/questionnaires/${questionnaireId}/${version}/questions`)
      .pipe(
        map((response) => normalizeKeys(response) as { variableName: string; answers: QuestionOption[] | null }[]),
        map((questions) => questions.find((question) => question.variableName === variableName)?.answers ?? [])
      );
  }

  /** Rapport PDF généré à la demande avec les filtres courants. */
  report(questionnaireId: string, version: number, filters: AnalysisFilters): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/questionnaires/${questionnaireId}/${version}/report.pdf`, {
      params: filterParams(filters),
      responseType: 'blob'
    });
  }

  archivedReports(questionnaireId: string, version: number): Observable<ArchivedReport[]> {
    return this.http
      .get<unknown>(`${this.baseUrl}/questionnaires/${questionnaireId}/${version}/reports`)
      .pipe(map((response) => normalizeKeys(response) as ArchivedReport[]));
  }

  archivedReport(id: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/reports/${id}`, { responseType: 'blob' });
  }

  syncStatus(questionnaireId: string, version: number): Observable<SyncStatus> {
    return this.http
      .get<unknown>(`${this.baseUrl}/questionnaires/${questionnaireId}/${version}/sync`)
      .pipe(map((response) => normalizeKeys(response) as SyncStatus));
  }

  startSync(questionnaireId: string, version: number): Observable<SyncStatus> {
    return this.http
      .post<unknown>(`${this.baseUrl}/questionnaires/${questionnaireId}/${version}/sync`, null)
      .pipe(map((response) => normalizeKeys(response) as SyncStatus));
  }

  exportData(questionnaireId: string, version: number, format: 'csv' | 'xlsx'): Observable<Blob> {
    const params = new HttpParams().set('format', format);
    return this.http.get(
      `${this.baseUrl}/questionnaires/${questionnaireId}/${version}/export`,
      { params, responseType: 'blob' }
    );
  }
}
