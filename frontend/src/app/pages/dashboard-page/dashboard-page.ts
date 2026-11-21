import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Dashboard } from '../../core/models/dashboard.models';
import { CatalogService } from '../../core/services/catalog.service';
import { statusOptions, statusPieOptions, trendOptions } from '../../shared/utils/chart-options';
import { DashboardChartComponent } from '../../shared/components/dashboard-chart/dashboard-chart';
import { KpiCardComponent } from '../../shared/components/kpi-card/kpi-card';
import { LoadingStateComponent } from '../../shared/components/loading-state/loading-state';
import { ScoreGaugeComponent } from '../../shared/components/score-gauge/score-gauge';
import { QuestionnaireCatalogComponent } from '../questionnaires/catalog-table/questionnaire-catalog';
import { SearchSelectComponent, SearchSelectOption } from '../../shared/components/search-select/search-select';

interface Indicator {
  label: string;
  value: number;
  tone: string;
}

const APPROVED = ['ApprovedBySupervisor', 'ApprovedByHeadquarters'];
const REJECTED = ['RejectedBySupervisor', 'RejectedByHeadquarters'];
/** Interviews remontées au moins une fois par l'enquêteur. */
const RETURNED = ['Completed', ...APPROVED, ...REJECTED];

/** Tableau de bord : activité de tous les questionnaires importés dans HQ. */
@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    DashboardChartComponent,
    KpiCardComponent,
    LoadingStateComponent,
    QuestionnaireCatalogComponent,
    ScoreGaugeComponent,
    SearchSelectComponent
  ],
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.css'
})
export class DashboardPageComponent implements OnInit {
  protected readonly catalog = inject(CatalogService);
  protected readonly statusOptions = statusOptions;
  protected readonly statusPieOptions = statusPieOptions;
  protected readonly trendOptions = trendOptions;
  protected readonly statusChart = signal<'bar' | 'pie'>('bar');
  protected readonly approvedStatuses = APPROVED;
  protected readonly rejectedStatuses = REJECTED;

  protected get questionnaireOptions(): SearchSelectOption[] {
    return [
      { value: '', label: 'Tous les questionnaires' },
      ...this.catalog.questionnaires().map((questionnaire) => ({
        value: `${questionnaire.questionnaireId}$${questionnaire.version}`,
        label: `${questionnaire.title} · v${questionnaire.version}`
      }))
    ];
  }

  ngOnInit(): void {
    if (!this.catalog.dashboard() && !this.catalog.loading()) {
      this.catalog.refresh();
    }
  }

  protected count(data: Dashboard, statuses: string[]): number {
    return data.statuses.filter((item) => statuses.includes(item.status)).reduce((sum, item) => sum + item.count, 0);
  }

  /** Part (0-100) des interviews dans ces statuts. */
  protected share(data: Dashboard, statuses: string[]): number {
    return data.interviewCount ? (this.count(data, statuses) * 100) / data.interviewCount : 0;
  }

  protected indicators(data: Dashboard): Indicator[] {
    const returned = this.share(data, RETURNED);
    return [
      { label: 'Interviews remontées', value: returned, tone: 'blue' },
      { label: 'Approuvées', value: this.share(data, APPROVED), tone: 'green' },
      { label: 'En cours sur le terrain', value: Math.max(0, 100 - returned), tone: 'violet' },
      { label: 'Rejetées', value: this.share(data, REJECTED), tone: 'orange' }
    ];
  }

  /** Approuvées ÷ remontées × 100 ; null s'il n'y a aucune interview remontée. */
  protected approvalScore(data: Dashboard): number | null {
    const returned = this.count(data, RETURNED);
    return returned ? (this.count(data, APPROVED) * 100) / returned : null;
  }

  protected level(score: number | null): string {
    if (score === null) {
      return 'Aucune interview remontée';
    }
    return score >= 70 ? 'Validation avancée' : score >= 40 ? 'Validation en cours' : 'Validation à engager';
  }
}
