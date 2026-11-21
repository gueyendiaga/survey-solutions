import { DatePipe } from '@angular/common';
import { Component, Input, OnChanges, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Questionnaire } from '../../../core/models/dashboard.models';
import { TablePagerComponent } from '../../../shared/components/table-pager/table-pager';
import { TableSearchComponent } from '../../../shared/components/table-search/table-search';
import { createTableState } from '../../../shared/utils/table-state';

/** Carte « questionnaires HQ » : recherche, tableau paginé ; « Ouvrir » mène à la page du questionnaire. */
@Component({
  selector: 'app-questionnaire-catalog',
  standalone: true,
  imports: [DatePipe, RouterLink, TablePagerComponent, TableSearchComponent],
  templateUrl: './questionnaire-catalog.html',
  styleUrl: './questionnaire-catalog.css'
})
export class QuestionnaireCatalogComponent implements OnChanges {
  @Input({ required: true }) questionnaires: Questionnaire[] = [];
  @Input() heading = 'Questionnaires';
  @Input() subtitle = '';

  private readonly rows = signal<Questionnaire[]>([]);
  protected readonly table = createTableState(
    () => this.rows(),
    (questionnaire) => `${questionnaire.title} ${questionnaire.variable ?? ''} v${questionnaire.version}`
  );

  ngOnChanges(): void {
    this.rows.set(this.questionnaires);
  }
}
