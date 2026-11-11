import { Injectable, inject, signal } from '@angular/core';
import { Observable, map, of, shareReplay, tap, catchError, finalize } from 'rxjs';
import { DashboardApiService } from './dashboard-api.service';
import { Dashboard, Questionnaire } from '../models/dashboard.models';

/**
 * Catalogue des questionnaires et indicateurs globaux, partagé par le tableau de bord, la page
 * Questionnaires, le menu latéral et la page d'un questionnaire (accès direct par URL).
 */
@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly api = inject(DashboardApiService);

  readonly dashboard = signal<Dashboard | null>(null);
  readonly questionnaires = signal<Questionnaire[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  /** Questionnaire filtrant le tableau de bord (« id$version »), vide pour tous. */
  readonly selectedKey = signal('');

  private pending: Observable<Questionnaire[]> | null = null;

  static key(questionnaire: Pick<Questionnaire, 'questionnaireId' | 'version'>): string {
    return `${questionnaire.questionnaireId}$${questionnaire.version}`;
  }

  /** Recharge le catalogue et les indicateurs (filtrés par le questionnaire choisi, le cas échéant). */
  refresh(): void {
    const selected = this.questionnaires().find((item) => CatalogService.key(item) === this.selectedKey());
    this.loading.set(true);
    this.error.set(null);
    this.dashboard.set(null);
    this.pending = null;
    this.api.dashboard(selected?.questionnaireId, selected?.version).subscribe({
      next: (dashboard) => {
        this.dashboard.set(dashboard);
        this.questionnaires.set(dashboard.questionnaires);
        if (!dashboard.questionnaires.some((item) => CatalogService.key(item) === this.selectedKey())) {
          this.selectedKey.set('');
        }
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Impossible de charger les données. Vérifiez la connexion à HQ et les permissions du compte API.');
      }
    });
  }

  /** Filtre le tableau de bord sur un questionnaire ; la liste complète reste disponible. */
  selectQuestionnaire(key: string): void {
    this.selectedKey.set(key);
    const questionnaire = this.questionnaires().find((item) => CatalogService.key(item) === key);
    if (!key || !questionnaire) {
      this.refresh();
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

  /** Charge le catalogue une seule fois (accès direct à l'URL d'un questionnaire). */
  ensureLoaded(): Observable<Questionnaire[]> {
    if (this.questionnaires().length) {
      return of(this.questionnaires());
    }
    if (!this.pending) {
      this.loading.set(true);
      this.pending = this.api.dashboard().pipe(
        tap((dashboard) => {
          this.dashboard.set(dashboard);
          this.questionnaires.set(dashboard.questionnaires);
        }),
        map((dashboard) => dashboard.questionnaires),
        catchError(() => {
          this.error.set('Impossible de charger les questionnaires depuis HQ.');
          this.pending = null;
          return of([] as Questionnaire[]);
        }),
        finalize(() => this.loading.set(false)),
        shareReplay(1)
      );
    }
    return this.pending;
  }

  find(questionnaireId: string, version: number): Observable<Questionnaire | null> {
    return this.ensureLoaded().pipe(
      map((questionnaires) => questionnaires.find((item) =>
        item.questionnaireId.toLowerCase() === questionnaireId.toLowerCase() && item.version === version) ?? null)
    );
  }
}