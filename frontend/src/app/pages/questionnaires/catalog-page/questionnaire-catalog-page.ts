import { Component, OnInit, inject } from '@angular/core';
import { CatalogService } from '../../../core/services/catalog.service';
import { LoadingStateComponent } from '../../../shared/components/loading-state/loading-state';
import { QuestionnaireCatalogComponent } from '../catalog-table/questionnaire-catalog';

/** Catalogue des questionnaires et versions disponibles dans HQ. */
@Component({
  selector: 'app-questionnaire-catalog-page',
  standalone: true,
  imports: [LoadingStateComponent, QuestionnaireCatalogComponent],
  templateUrl: './questionnaire-catalog-page.html',
  styleUrl: './questionnaire-catalog-page.css'
})
export class QuestionnaireCatalogPageComponent implements OnInit {
  protected readonly catalog = inject(CatalogService);

  ngOnInit(): void {
    if (!this.catalog.questionnaires().length && !this.catalog.loading()) {
      this.catalog.refresh();
    }
  }
}
