import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, Input, signal } from '@angular/core';
import type { EChartsCoreOption } from 'echarts/core';
import { DashboardChartComponent } from '../../../shared/components/dashboard-chart/dashboard-chart';
import { DailyProgress, FieldworkOverview, InterviewerProgress, TeamProgress } from '../../../core/models/dashboard.models';

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
  templateUrl: './fieldwork-panel.html',
  styleUrl: './fieldwork-panel.css'
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
        lineStyle: { color: '#9aa5dc', width: 2 },
        itemStyle: { color: '#9aa5dc' },
        areaStyle: { color: 'rgba(214, 219, 242, 0.75)' },
        symbol: 'none',
        z: 1,
        endLabel: { show: true, formatter: (item: { value: number }) => `Cumul ${item.value}`, color: '#16267a', fontWeight: 600 }
      },
      {
        name: 'Terminées dans la journée',
        type: 'bar',
        data: days.map((day) => day.completed),
        itemStyle: { color: '#1e3398', borderRadius: [6, 6, 0, 0] },
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
