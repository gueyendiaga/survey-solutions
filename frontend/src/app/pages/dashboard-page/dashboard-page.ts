import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CatalogService } from '../../core/services/catalog.service';
import { statusOptions, trendOptions } from '../../shared/utils/chart-options';
import { DashboardChartComponent } from '../../shared/components/dashboard-chart/dashboard-chart';
import { LoadingStateComponent } from '../../shared/components/loading-state/loading-state';
import { QuestionnaireCatalogComponent } from '../questionnaire-catalog/questionnaire-catalog';
import { SearchSelectComponent, SearchSelectOption } from '../../shared/components/search-select/search-select';

/** Tableau de bord : activité de tous les questionnaires importés dans HQ. */
@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [DatePipe, DecimalPipe, FormsModule, DashboardChartComponent, LoadingStateComponent, QuestionnaireCatalogComponent, SearchSelectComponent],
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.css'
})
export class DashboardPageComponent implements OnInit {
  protected readonly catalog = inject(CatalogService);
  protected readonly statusOptions = statusOptions;
  protected readonly trendOptions = trendOptions;

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
}
