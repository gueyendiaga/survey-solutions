import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import {
  CompletenessOverview,
  Dashboard,
  QuestionAnalytics,
  Questionnaire,
  QuestionSummary
} from './dashboard.models';

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
    console.log('DashboardApiService.dashboard', { baseUrl: this.baseUrl, questionnaireId, version, params: params.toString() });
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
    variableName: string
  ): Observable<QuestionAnalytics> {
    const params = new HttpParams().set('variableName', variableName);
    return this.http
      .get<unknown>(
        `${this.baseUrl}/questionnaires/${questionnaireId}/${version}/question-analytics`,
        { params }
      )
      .pipe(map((response) => normalizeKeys(response) as QuestionAnalytics));
  }

  questionCompleteness(
    questionnaireId: string,
    version: number
  ): Observable<CompletenessOverview> {
    return this.http
      .get<unknown>(
        `${this.baseUrl}/questionnaires/${questionnaireId}/${version}/question-completeness`
      )
      .pipe(map((response) => normalizeKeys(response) as CompletenessOverview));
  }

  exportData(questionnaireId: string, version: number, format: 'csv' | 'xlsx'): Observable<Blob> {
    const params = new HttpParams().set('format', format);
    return this.http.get(
      `${this.baseUrl}/questionnaires/${questionnaireId}/${version}/export`,
      { params, responseType: 'blob' }
    );
  }
}
