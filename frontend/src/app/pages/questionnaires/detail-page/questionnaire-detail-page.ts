import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CatalogService } from '../../../core/services/catalog.service';
import { FilterBarComponent } from '../../../shared/components/filter-bar/filter-bar';
import { statusLabel } from '../../../shared/utils/labels';
import { LoadingStateComponent } from '../../../shared/components/loading-state/loading-state';
import { FieldworkPanelComponent } from '../tabs/fieldwork-panel/fieldwork-panel';
import { QualityTabComponent } from '../tabs/quality-tab/quality-tab';
import { QuestionnaireHeaderComponent } from '../questionnaire-header/questionnaire-header';
import { DEFAULT_TAB_SLUG, QuestionnaireTab, slugFor, tabFromSlug } from '../questionnaire-tabs';
import { QuestionnaireStore } from '../questionnaire.store';
import { ReportsTabComponent } from '../tabs/reports-tab/reports-tab';
import { VariablesTabComponent } from '../tabs/variables-tab/variables-tab';

/**
 * Page d'un questionnaire : /questionnaires/:questionnaireId/:version/:tab.
 * Fournit le QuestionnaireStore, résout le questionnaire depuis le catalogue (accès direct par
 * URL compris) et synchronise l'onglet affiché avec l'URL.
 */
@Component({
  selector: 'app-questionnaire-detail-page',
  standalone: true,
  imports: [
    RouterLink,
    QuestionnaireHeaderComponent,
    FilterBarComponent,
    LoadingStateComponent,
    FieldworkPanelComponent,
    VariablesTabComponent,
    QualityTabComponent,
    ReportsTabComponent
  ],
  providers: [QuestionnaireStore],
  templateUrl: './questionnaire-detail-page.html',
  styleUrl: './questionnaire-detail-page.css'
})
export class QuestionnaireDetailPageComponent {
  /** Paramètres de route, liés par withComponentInputBinding(). */
  readonly questionnaireId = input.required<string>();
  readonly version = input.required<string>();
  readonly tab = input<string>(DEFAULT_TAB_SLUG);

  protected readonly store = inject(QuestionnaireStore);
  protected readonly catalog = inject(CatalogService);
  private readonly router = inject(Router);

  protected readonly notFound = signal(false);
  protected readonly statusLabel = statusLabel;

  constructor() {
    // Onglet : un segment inconnu renvoie vers le premier onglet.
    effect(() => {
      const tab = tabFromSlug(this.tab());
      untracked(() => {
        if (tab) {
          this.store.setTab(tab);
        } else {
          void this.router.navigate(this.tabUrl('fieldwork'), { replaceUrl: true });
        }
      });
    });

    // Questionnaire : résolu dans le catalogue, chargé à l'ouverture ou au changement d'URL.
    effect((onCleanup) => {
      const questionnaireId = this.questionnaireId();
      const version = Number(this.version());
      untracked(() => {
        this.notFound.set(false);
        const subscription = this.catalog.find(questionnaireId, version).subscribe((questionnaire) => {
          if (questionnaire) {
            this.store.open(questionnaire);
          } else {
            this.notFound.set(true);
          }
        });
        onCleanup(() => subscription.unsubscribe());
      });
    });
  }

  protected selectTab(tab: QuestionnaireTab): void {
    void this.router.navigate(this.tabUrl(tab));
  }

  protected backToCatalog(): void {
    void this.router.navigate(['/questionnaires']);
  }

  /** Depuis l'onglet Qualité : ouvre la variable dans l'onglet Variables. */
  protected openVariable(variableName: string): void {
    if (this.store.selectVariable(variableName)) {
      this.selectTab('variables');
    }
  }

  private tabUrl(tab: QuestionnaireTab): string[] {
    return ['/questionnaires', this.questionnaireId(), this.version(), slugFor(tab)];
  }
}
