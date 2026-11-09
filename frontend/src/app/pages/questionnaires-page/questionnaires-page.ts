import { Component, OnInit, inject } from '@angular/core';
import { CatalogService } from '../../core/services/catalog.service';
import { LoadingStateComponent } from '../../shared/components/loading-state/loading-state';
import { QuestionnaireCatalogComponent } from '../questionnaire-catalog/questionnaire-catalog';

/** Catalogue des questionnaires et versions disponibles dans HQ. */
@Component({
  selector: 'app-questionnaires-page',
  standalone: true,
  imports: [LoadingStateComponent, QuestionnaireCatalogComponent],
  templateUrl: './questionnaires-page.html',
  styleUrl: './questionnaires-page.css'
})
export class QuestionnairesPageComponent implements OnInit {
  protected readonly catalog = inject(CatalogService);

  ngOnInit(): void {
    if (!this.catalog.questionnaires().length && !this.catalog.loading()) {
      this.catalog.refresh();
    }
  }
}
