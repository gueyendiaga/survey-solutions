import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, Input, signal } from '@angular/core';
import type { EChartsCoreOption } from 'echarts/core';
import { DashboardChartComponent } from './dashboard-chart';
import { DailyProgress, FieldworkOverview, InterviewerProgress, TeamProgress } from './dashboard.models';

/** Point d'attention calculé à partir des chiffres du suivi. */
export interface AttentionPoint {
  tone: 'warn' | 'ok';
  text: string;
}

type Period = '7' | '30' | 'all';

/** Suivi de collecte : réalisation par rapport aux affectations, par équipe et par enquêteur. */
@Component({
  selector: 'app-fieldwork-panel',
  standalone: true,
  imports: [DatePipe, DecimalPipe, DashboardChartComponent],
  template: `
    <section class="kpis" aria-label="Chiffres clés">
      <article class="card kpi">
        <div class="kpi-top">
          <span class="kpi-label">Taux de réalisation</span>
          <span class="tile"><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9" /><circle cx="12" cy="12" r="4" /></svg></span>
        </div>
        <strong class="kpi-value">{{ overview.completionRate === null ? '—' : (overview.completionRate | number: '1.0-1' : 'fr-FR') + ' %' }}</strong>
        <div class="track"><div class="fill" [style.width.%]="cap(overview.completionRate)"></div></div>
        <span class="kpi-note">{{ overview.completed | number: '1.0-0' : 'fr-FR' }} sur {{ overview.target | number: '1.0-0' : 'fr-FR' }} interviews prévues{{ overview.unlimitedAssignments ? ' (+ ' + overview.unlimitedAssignments + ' sans quota)' : '' }}</span>
      </article>
      <article class="card kpi">
        <div class="kpi-top">
          <span class="kpi-label">Interviews réalisées</span>
          <span class="tile"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 11l3 3 8-8" /><path d="M20 12v7a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h9" /></svg></span>
        </div>
        <strong class="kpi-value">{{ overview.completed | number: '1.0-0' : 'fr-FR' }}</strong>
        <span class="kpi-note">
          @if (lastDay(); as day) {
            <span class="pill ok">+{{ day.completed }}</span> le {{ day.date | date: 'd MMMM' : 'UTC' : 'fr-FR' }}
          } @else {
            Aucune interview terminée
          }
        </span>
      </article>
      <article class="card kpi">
        <div class="kpi-top">
          <span class="kpi-label">Approuvées</span>
          <span class="tile ok"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6z" /><path d="M9 12l2 2 4-4" /></svg></span>
        </div>
        <strong class="kpi-value">{{ overview.approved | number: '1.0-0' : 'fr-FR' }}</strong>
        <span class="kpi-note">{{ approvedShare() }} des interviews réalisées</span>
      </article>
      <article class="card kpi">
        <div class="kpi-top">
          <span class="kpi-label">À traiter</span>
          <span class="tile warn"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 9v4M12 17h.01" /><path d="M10.3 3.9L2.5 17.5A2 2 0 0 0 4.2 20.5h15.6a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z" /></svg></span>
        </div>
        <strong class="kpi-value">{{ overview.rejected + overview.inProgress | number: '1.0-0' : 'fr-FR' }}</strong>
        <span class="kpi-note">{{ overview.rejected }} rejetée(s) · {{ overview.inProgress }} en cours · {{ errors() }} en erreur</span>
      </article>
    </section>

    @if (overview.filtersApplied) {
      <p class="footnote fieldwork-note">Filtres actifs : les interviews sont filtrées ; les objectifs ne dépendent que de l’équipe ou de l’enquêteur choisi.</p>
    }

    <div class="grid-2-1">
      <section class="card pad">
        <div class="card-head">
          <div>
            <h2>Progression de la collecte</h2>
            <p>Interviews terminées par jour et cumul, rapportés à l’objectif</p>
          </div>
          <div class="segmented" role="group" aria-label="Période">
            <button type="button" [attr.aria-pressed]="period() === '7'" (click)="period.set('7')">7 jours</button>
            <button type="button" [attr.aria-pressed]="period() === '30'" (click)="period.set('30')">30 jours</button>
            <button type="button" [attr.aria-pressed]="period() === 'all'" (click)="period.set('all')">Tout</button>
          </div>
        </div>
        <div class="legend">
          <span><i class="sw primary"></i>Terminées dans la journée</span>
          <span><i class="sw soft"></i>Cumul</span>
          @if (overview.target > 0) {
            <span><i class="sw dash"></i>Objectif</span>
          }
        </div>
        @if (overview.daily.length) {
          <app-dashboard-chart [options]="progressOptions()" [height]="270" downloadFilename="progression-collecte" />
        } @else {
          <p class="empty">Aucune interview terminée sur la période.</p>
        }
      </section>

      <section class="card pad">
        <h2>Avancement</h2>
        <div class="donut-row">
          <svg width="112" height="112" viewBox="0 0 112 112" role="img" [attr.aria-label]="rate(overview.completionRate) + ' de l’objectif atteint'">
            <circle cx="56" cy="56" r="44" fill="none" stroke="#eef1f6" stroke-width="12" />
            <circle cx="56" cy="56" r="44" fill="none" stroke="var(--primary)" stroke-width="12" stroke-linecap="round"
              [attr.stroke-dasharray]="donutDash()" transform="rotate(-90 56 56)" />
            <text x="56" y="56" text-anchor="middle" class="donut-value">{{ rate(overview.completionRate) }}</text>
            <text x="56" y="74" text-anchor="middle" class="donut-label">objectif</text>
          </svg>
          <div class="donut-legend">
            <div><i class="sw primary"></i>Réalisées <strong>{{ overview.completed | number: '1.0-0' : 'fr-FR' }}</strong></div>
            <div><i class="sw empty-sw"></i>Restantes <strong>{{ remaining() | number: '1.0-0' : 'fr-FR' }}</strong></div>
            @if (daysNeeded(); as days) {
              <span class="kpi-note">≈ {{ days }} jour(s) au rythme actuel</span>
            }
          </div>
        </div>
        <h3>Points d’attention</h3>
        <ul class="attention">
          @for (point of attention(); track point.text) {
            <li [class.ok]="point.tone === 'ok'">
              @if (point.tone === 'ok') {
                <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M8.5 12l2.5 2.5 4.5-5" /></svg>
              } @else {
                <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M12 8v5M12 16h.01" /></svg>
              }
              <span>{{ point.text }}</span>
            </li>
          }
        </ul>
      </section>
    </div>

    <section class="card">
      <div class="card-head pad">
        <div>
          <h2>Équipes et enquêteurs</h2>
          <p>Réalisation par rapport aux affectations, qualité et rythme</p>
        </div>
        <span class="count">{{ overview.teams.length }} équipe(s) · {{ interviewerCount() }} enquêteur(s)</span>
      </div>
      @if (overview.teams.length) {
        <div class="table-scroll">
          <table class="data fieldwork-table">
            <thead>
              <tr>
                <th class="l">Équipe / enquêteur</th>
                <th>Objectif</th>
                <th>Réalisées</th>
                <th class="l rate-col">Réalisation</th>
                <th>Approuvées</th>
                <th>À traiter</th>
                <th>Durée méd.</th>
                <th>Par jour</th>
                <th class="l">Statut</th>
              </tr>
            </thead>
            <tbody>
              @for (team of overview.teams; track team.supervisor) {
                <tr class="team-row">
                  <td class="l">
                    <button type="button" class="person team-toggle" [attr.aria-expanded]="isOpen(team)" (click)="toggle(team)">
                      <svg class="chev" viewBox="0 0 24 24" aria-hidden="true" [class.open]="isOpen(team)"><path d="M9 6l6 6-6 6" /></svg>
                      <span class="avatar strong">{{ initials(team.supervisor) }}</span>
                      <span class="who">Équipe {{ team.supervisor }}<small>Superviseur · {{ team.interviewers.length }} enquêteur(s)</small></span>
                    </button>
                  </td>
                  <td>{{ team.target | number: '1.0-0' : 'fr-FR' }}</td>
                  <td>{{ team.completed | number: '1.0-0' : 'fr-FR' }}</td>
                  <td class="l"><span class="bar-cell"><span class="track"><span class="fill" [style.width.%]="cap(team.completionRate)"></span></span>{{ rate(team.completionRate) }}</span></td>
                  <td>{{ team.approved | number: '1.0-0' : 'fr-FR' }}</td>
                  <td>{{ team.rejected + team.inProgress | number: '1.0-0' : 'fr-FR' }}</td>
                  <td></td><td></td>
                  <td class="l"><span class="pill" [class]="'pill ' + teamStatus(team).tone"><i class="dot"></i>{{ teamStatus(team).label }}</span></td>
                </tr>
                @if (isOpen(team)) {
                  @for (row of team.interviewers; track row.interviewer) {
                    <tr class="interviewer-row" [class.not-started]="notStarted(row)">
                      <td class="l">
                        <span class="person indent">
                          <span class="avatar" [class.warn]="notStarted(row)">{{ initials(row.interviewer) }}</span>
                          <span class="who">{{ row.interviewer }}<small>{{ row.lastCompletion ? 'Dernière interview le ' + (row.lastCompletion | date: 'd MMM' : 'UTC' : 'fr-FR') : 'Aucune interview' }}</small></span>
                        </span>
                      </td>
                      <td>{{ row.target | number: '1.0-0' : 'fr-FR' }}</td>
                      <td>{{ row.completed | number: '1.0-0' : 'fr-FR' }}</td>
                      <td class="l"><span class="bar-cell"><span class="track"><span class="fill" [class.warn]="row.completionRate !== null && row.completionRate < 50" [style.width.%]="cap(row.completionRate)"></span></span>{{ rate(row.completionRate) }}</span></td>
                      <td>{{ row.approved | number: '1.0-0' : 'fr-FR' }}</td>
                      <td>{{ row.rejected + row.inProgress | number: '1.0-0' : 'fr-FR' }}</td>
                      <td>{{ row.medianDurationMinutes === null ? '—' : (row.medianDurationMinutes | number: '1.0-1' : 'fr-FR') + ' min' }}</td>
                      <td>{{ row.completedPerActiveDay === null ? '—' : (row.completedPerActiveDay | number: '1.0-1' : 'fr-FR') }}</td>
                      <td class="l"><span [class]="'pill ' + interviewerStatus(row).tone"><i class="dot"></i>{{ interviewerStatus(row).label }}</span></td>
                    </tr>
                  }
                  @if (unassigned(team) > 0) {
                    <tr class="interviewer-row not-started">
                      <td class="l">
                        <span class="person indent">
                          <span class="avatar empty-avatar">—</span>
                          <span class="who">Non confiée<small>Chez le superviseur</small></span>
                        </span>
                      </td>
                      <td>{{ unassigned(team) | number: '1.0-0' : 'fr-FR' }}</td>
                      <td>—</td><td class="l">—</td><td>—</td><td>—</td><td>—</td><td>—</td>
                      <td class="l"><span class="pill warn"><i class="dot"></i>À attribuer</span></td>
                    </tr>
                  }
                }
              }
            </tbody>
          </table>
        </div>
        <p class="footnote pad-x">Réalisées : interviews terminées au moins une fois, quel que soit leur statut actuel. « Par jour » rapporte les réalisées aux jours où l’enquêteur en a terminé au moins une.</p>
      } @else {
        <p class="empty">Aucune affectation ni interview pour ces filtres.</p>
      }
    </section>
  `
})
export class FieldworkPanelComponent {
  @Input({ required: true }) overview!: FieldworkOverview;

  protected readonly period = signal<Period>('all');
  private readonly collapsed = signal<ReadonlySet<string>>(new Set());

  /** Jours consécutifs : un jour sans complétion apparaît à zéro (31 derniers jours au plus). */
  static continuousDays(daily: DailyProgress[]): DailyProgress[] {
    if (!daily.length) {
      return [];
    }
    const byDate = new Map(daily.map((day) => [day.date, day]));
    const last = new Date(`${daily[daily.length - 1].date}T00:00:00Z`);
    let cursor = new Date(`${daily[0].date}T00:00:00Z`);
    const earliest = new Date(last.getTime() - 30 * 86_400_000);
    if (cursor < earliest) {
      cursor = earliest;
    }
    let cumulative = daily.filter((day) => new Date(`${day.date}T00:00:00Z`) < cursor)
      .reduce((max, day) => Math.max(max, day.cumulative), 0);
    const days: DailyProgress[] = [];
    for (; cursor <= last; cursor = new Date(cursor.getTime() + 86_400_000)) {
      const key = cursor.toISOString().slice(0, 10);
      const day = byDate.get(key);
      cumulative = day ? day.cumulative : cumulative;
      days.push({ date: key, completed: day?.completed ?? 0, cumulative });
    }
    return days;
  }

  protected isOpen(team: TeamProgress): boolean {
    return !this.collapsed().has(team.supervisor);
  }

  protected toggle(team: TeamProgress): void {
    const next = new Set(this.collapsed());
    if (next.has(team.supervisor)) {
      next.delete(team.supervisor);
    } else {
      next.add(team.supervisor);
    }
    this.collapsed.set(next);
  }

  protected notStarted(row: InterviewerProgress): boolean {
    return row.target > 0 && row.total === 0;
  }

  /** Affectations détenues par le superviseur, pas encore confiées à un enquêteur. */
  protected unassigned(team: TeamProgress): number {
    return Math.max(0, team.target - team.interviewers.reduce((sum, row) => sum + row.target, 0));
  }

  protected interviewerCount(): number {
    return this.overview.teams.reduce((sum, team) => sum + team.interviewers.length, 0);
  }

  protected initials(name: string): string {
    const clean = name.replace(/[^A-Za-zÀ-ÿ0-9]/g, '');
    const letters = clean.replace(/[0-9]/g, '');
    const digits = clean.replace(/[^0-9]/g, '');
    return ((letters.charAt(0) || '?') + (digits.slice(-1) || letters.charAt(1) || '')).toUpperCase();
  }

  protected interviewerStatus(row: InterviewerProgress): { tone: string; label: string } {
    if (this.notStarted(row)) {
      return { tone: 'warn', label: 'Pas commencé' };
    }
    if (row.rejected > 0) {
      return { tone: 'warn', label: 'À corriger' };
    }
    if (row.target > 0 && row.completed >= row.target) {
      return { tone: 'ok', label: 'Objectif atteint' };
    }
    return { tone: 'info', label: 'En cours' };
  }

  protected teamStatus(team: TeamProgress): { tone: string; label: string } {
    if (team.target > 0 && team.completed >= team.target) {
      return { tone: 'ok', label: 'Objectif atteint' };
    }
    return team.rejected > 0 ? { tone: 'warn', label: 'À corriger' } : { tone: 'info', label: 'En cours' };
  }

  protected rate(value: number | null): string {
    return value === null ? '—' : `${value.toLocaleString('fr-FR', { maximumFractionDigits: 1 })} %`;
  }

  protected cap(value: number | null): number {
    return value === null ? 0 : Math.max(0, Math.min(100, value));
  }

  protected errors(): number {
    return this.overview.teams.flatMap((team) => team.interviewers).reduce((sum, row) => sum + row.withErrors, 0);
  }

  protected approvedShare(): string {
    return this.overview.completed ? this.rate(Math.round(this.overview.approved * 1000 / this.overview.completed) / 10) : '—';
  }

  protected remaining(): number {
    return Math.max(0, this.overview.target - this.overview.completed);
  }

  /** Jours de collecte restants au rythme moyen des jours actifs. */
  protected daysNeeded(): number | null {
    const active = this.overview.daily.filter((day) => day.completed > 0).length;
    if (!active || !this.remaining()) {
      return null;
    }
    return Math.ceil(this.remaining() / (this.overview.completed / active));
  }

  protected lastDay(): DailyProgress | null {
    const days = this.overview.daily.filter((day) => day.completed > 0);
    return days.length ? days[days.length - 1] : null;
  }

  protected donutDash(): string {
    const circumference = 2 * Math.PI * 44;
    return `${(circumference * this.cap(this.overview.completionRate)) / 100} ${circumference}`;
  }

  /** Mêmes règles que le rapport PDF : enquêteurs inactifs, affectations non confiées, rejets, erreurs. */
  protected attention(): AttentionPoint[] {
    const rows = this.overview.teams.flatMap((team) => team.interviewers);
    const points: AttentionPoint[] = [];
    rows.filter((row) => this.notStarted(row)).forEach((row) =>
      points.push({ tone: 'warn', text: `${row.interviewer} n’a pas commencé (${row.target} interview(s) affectée(s)).` }));
    this.overview.teams.filter((team) => this.unassigned(team) > 0).forEach((team) =>
      points.push({ tone: 'warn', text: `${this.unassigned(team)} affectation(s) restent chez ${team.supervisor}.` }));
    const lagging = rows.filter((row) => row.total > 0 && row.completionRate !== null && row.completionRate < 50);
    if (lagging.length) {
      points.push({ tone: 'warn', text: `${lagging.length} enquêteur(s) sous 50 % de leur objectif.` });
    }
    if (this.overview.rejected > 0) {
      points.push({ tone: 'warn', text: `${this.overview.rejected} interview(s) rejetée(s) à corriger.` });
    }
    if (this.errors() > 0) {
      points.push({ tone: 'warn', text: `${this.errors()} interview(s) avec erreurs de validation.` });
    }
    if (this.overview.rejected === 0 && this.errors() === 0) {
      points.push({ tone: 'ok', text: 'Aucune interview rejetée ni en erreur.' });
    }
    return points;
  }

  protected progressOptions(): EChartsCoreOption {
    const all = FieldworkPanelComponent.continuousDays(this.overview.daily);
    const days = this.period() === 'all' ? all : all.slice(-Number(this.period()));
    const series: object[] = [
      {
        name: 'Cumul',
        type: 'line',
        data: days.map((day) => day.cumulative),
        lineStyle: { color: '#9db6d6', width: 2 },
        itemStyle: { color: '#9db6d6' },
        areaStyle: { color: 'rgba(216, 227, 240, 0.75)' },
        symbol: 'none',
        z: 1,
        endLabel: { show: true, formatter: (item: { value: number }) => `Cumul ${item.value}`, color: '#2d5283', fontWeight: 600 }
      },
      {
        name: 'Terminées dans la journée',
        type: 'bar',
        data: days.map((day) => day.completed),
        itemStyle: { color: '#3a669e', borderRadius: [6, 6, 0, 0] },
        barMaxWidth: 36,
        label: {
          show: days.length <= 16,
          position: 'top',
          color: '#1f2937',
          fontWeight: 600,
          formatter: (item: { value: number }) => (item.value ? `${item.value}` : '')
        },
        z: 2
      }
    ];
    if (this.overview.target > 0) {
      series.push({
        name: 'Objectif',
        type: 'line',
        data: days.map(() => this.overview.target),
        lineStyle: { type: 'dashed', color: '#c27c1e', width: 1.5 },
        itemStyle: { color: '#c27c1e' },
        symbol: 'none',
        endLabel: { show: true, formatter: `Objectif ${this.overview.target}`, color: '#8a4f0c', fontWeight: 600 }
      });
    }
    return {
      animation: false,
      textStyle: { fontFamily: 'Inter, sans-serif' },
      grid: { left: 36, right: 96, top: 24, bottom: 28 },
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: days.map((day) =>
          new Date(`${day.date}T00:00:00Z`).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', timeZone: 'UTC' })),
        axisLine: { lineStyle: { color: '#e6eaf0' } },
        axisTick: { show: false },
        axisLabel: { color: '#6b7280' }
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
        splitLine: { lineStyle: { color: '#f0f2f6' } },
        axisLabel: { color: '#6b7280' }
      },
      series
    };
  }
}
