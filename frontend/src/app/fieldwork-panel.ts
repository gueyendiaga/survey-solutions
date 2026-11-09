import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, Input, signal } from '@angular/core';
import type { EChartsCoreOption } from 'echarts/core';
import { DashboardChartComponent } from './dashboard-chart';
import { FieldworkOverview, TeamProgress } from './dashboard.models';

/** Suivi de collecte : réalisation par rapport aux affectations, par équipe et par enquêteur. */
@Component({
  selector: 'app-fieldwork-panel',
  standalone: true,
  imports: [DatePipe, DecimalPipe, DashboardChartComponent],
  template: `
    <div class="question-kpi-grid fieldwork-kpis">
      <article class="kpi-card">
        <div class="kpi-label">Objectif des affectations</div>
        <strong class="kpi-value">{{ overview.target | number: '1.0-0' : 'fr-FR' }}</strong>
        <span class="kpi-note">
          @if (overview.unlimitedAssignments) {
            + {{ overview.unlimitedAssignments }} affectation(s) sans quota
          } @else {
            Somme des quotas des affectations actives
          }
        </span>
      </article>
      <article class="kpi-card">
        <div class="kpi-label">Interviews réalisées</div>
        <strong class="kpi-value">{{ overview.completed | number: '1.0-0' : 'fr-FR' }}</strong>
        <span class="kpi-note">
          @if (overview.completionRate !== null) {
            {{ overview.completionRate | number: '1.0-1' : 'fr-FR' }} % de l’objectif
          } @else {
            Pas d’objectif chiffré
          }
        </span>
        @if (overview.completionRate !== null) {
          <span class="coverage-track"><span [style.width.%]="min(overview.completionRate, 100)"></span></span>
        }
      </article>
      <article class="kpi-card">
        <div class="kpi-label">Approuvées</div>
        <strong class="kpi-value">{{ overview.approved | number: '1.0-0' : 'fr-FR' }}</strong>
        <span class="kpi-note">Par le superviseur ou HQ</span>
      </article>
      <article class="kpi-card">
        <div class="kpi-label">Rejetées</div>
        <strong class="kpi-value">{{ overview.rejected | number: '1.0-0' : 'fr-FR' }}</strong>
        <span class="kpi-note">À corriger par l’enquêteur</span>
      </article>
      <article class="kpi-card">
        <div class="kpi-label">En cours</div>
        <strong class="kpi-value">{{ overview.inProgress | number: '1.0-0' : 'fr-FR' }}</strong>
        <span class="kpi-note">Jamais terminées</span>
      </article>
    </div>

    @if (overview.filtersApplied) {
      <p class="analysis-footnote fieldwork-note">
        Filtres actifs : les interviews sont filtrées. Les objectifs ne dépendent ni de la période ni de la
        zone, seulement de l’équipe ou de l’enquêteur choisi.
      </p>
    }

    <section class="panel answer-distribution-panel">
      <div class="panel-heading">
        <div>
          <h2>Progression de la collecte</h2>
          <p>Interviews terminées par jour et cumul, comparés à l’objectif</p>
        </div>
      </div>
      @if (overview.daily.length) {
        <app-dashboard-chart
          [options]="progressOptions()"
          [height]="280"
          downloadFilename="progression-collecte" />
      } @else {
        <div class="empty-state table-empty">Aucune interview terminée sur la période.</div>
      }
    </section>

    <section class="panel answer-distribution-panel">
      <div class="panel-heading">
        <div>
          <h2>Équipes et enquêteurs</h2>
          <p>Réalisation par rapport aux affectations, qualité et rythme de collecte</p>
        </div>
        <span class="table-count">{{ overview.teams.length }} équipe(s)</span>
      </div>
      @if (overview.teams.length) {
        <div class="distribution-table fieldwork-table">
          <table>
            <thead>
              <tr>
                <th>ÉQUIPE / ENQUÊTEUR</th>
                <th>OBJECTIF</th>
                <th>RÉALISÉES</th>
                <th>TAUX</th>
                <th>APPROUVÉES</th>
                <th>REJETÉES</th>
                <th>EN COURS</th>
                <th>AVEC ERREURS</th>
                <th>DURÉE MÉD.</th>
                <th>PAR JOUR</th>
                <th>DERNIÈRE</th>
              </tr>
            </thead>
            <tbody>
              @for (team of overview.teams; track team.supervisor) {
                <tr class="team-row">
                  <td>
                    <button
                      type="button"
                      class="team-toggle"
                      [attr.aria-expanded]="isOpen(team)"
                      (click)="toggle(team)">
                      <span aria-hidden="true">{{ isOpen(team) ? '▾' : '▸' }}</span>
                      {{ team.supervisor }}
                    </button>
                  </td>
                  <td>{{ team.target | number: '1.0-0' : 'fr-FR' }}</td>
                  <td>{{ team.completed | number: '1.0-0' : 'fr-FR' }}</td>
                  <td>{{ rate(team.completionRate) }}</td>
                  <td>{{ team.approved | number: '1.0-0' : 'fr-FR' }}</td>
                  <td>{{ team.rejected | number: '1.0-0' : 'fr-FR' }}</td>
                  <td>{{ team.inProgress | number: '1.0-0' : 'fr-FR' }}</td>
                  <td>{{ team.withErrors | number: '1.0-0' : 'fr-FR' }}</td>
                  <td colspan="3"></td>
                </tr>
                @if (isOpen(team)) {
                  @for (row of team.interviewers; track row.interviewer) {
                    <tr class="interviewer-row" [class.not-started]="row.target > 0 && row.total === 0">
                      <td class="interviewer-name">{{ row.interviewer }}</td>
                      <td>{{ row.target | number: '1.0-0' : 'fr-FR' }}</td>
                      <td>{{ row.completed | number: '1.0-0' : 'fr-FR' }}</td>
                      <td>{{ rate(row.completionRate) }}</td>
                      <td>{{ row.approved | number: '1.0-0' : 'fr-FR' }}</td>
                      <td>{{ row.rejected | number: '1.0-0' : 'fr-FR' }}</td>
                      <td>{{ row.inProgress | number: '1.0-0' : 'fr-FR' }}</td>
                      <td>{{ row.withErrors | number: '1.0-0' : 'fr-FR' }}</td>
                      <td>
                        {{ row.medianDurationMinutes === null ? '—' : (row.medianDurationMinutes | number: '1.0-1' : 'fr-FR') + ' min' }}
                      </td>
                      <td>
                        {{ row.completedPerActiveDay === null ? '—' : (row.completedPerActiveDay | number: '1.0-1' : 'fr-FR') }}
                      </td>
                      <td>{{ row.lastCompletion ? (row.lastCompletion | date: 'd MMM' : '' : 'fr-FR') : '—' }}</td>
                    </tr>
                  }
                }
              }
            </tbody>
          </table>
        </div>
        <p class="analysis-footnote">
          Réalisées : interviews terminées au moins une fois par l’enquêteur, quel que soit leur statut actuel.
          « Par jour » rapporte les interviews réalisées aux jours où l’enquêteur en a terminé au moins une.
          Une ligne grisée signale un enquêteur avec des affectations mais aucune interview.
        </p>
      } @else {
        <div class="empty-state table-empty">Aucune affectation ni interview pour ces filtres.</div>
      }
    </section>
  `
})
export class FieldworkPanelComponent {
  @Input({ required: true }) overview!: FieldworkOverview;

  private readonly collapsed = signal<ReadonlySet<string>>(new Set());

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

  protected rate(value: number | null): string {
    return value === null ? '—' : `${value.toLocaleString('fr-FR', { maximumFractionDigits: 1 })} %`;
  }

  protected min(a: number, b: number): number {
    return Math.min(a, b);
  }

  protected progressOptions(): EChartsCoreOption {
    const days = this.overview.daily;
    const series: object[] = [
      {
        name: 'Réalisées du jour',
        type: 'bar',
        data: days.map((day) => day.completed),
        itemStyle: { color: '#93b4f5', borderRadius: [3, 3, 0, 0] },
        barMaxWidth: 28
      },
      {
        name: 'Cumul',
        type: 'line',
        data: days.map((day) => day.cumulative),
        itemStyle: { color: '#2563eb' },
        symbolSize: 6
      }
    ];
    if (this.overview.target > 0) {
      series.push({
        name: 'Objectif',
        type: 'line',
        data: days.map(() => this.overview.target),
        lineStyle: { type: 'dashed', color: '#16a34a' },
        itemStyle: { color: '#16a34a' },
        symbol: 'none'
      });
    }
    return {
      animation: false,
      grid: { left: 48, right: 24, top: 24, bottom: 36 },
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: days.map((day) =>
          new Date(`${day.date}T00:00:00Z`).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', timeZone: 'UTC' })
        ),
        axisLine: { lineStyle: { color: '#dfe7f3' } },
        axisLabel: { color: '#687b9a' }
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
        splitLine: { lineStyle: { color: '#eef2f8' } },
        axisLabel: { color: '#687b9a' }
      },
      series
    };
  }
}
