import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AnalysisFilters,
  FilterOptions,
  NO_FILTERS,
  QuestionOption,
  QuestionSummary
} from './dashboard.models';

/**
 * Barre de filtres commune au suivi terrain et à l'analyse des variables. Les choix sont
 * appliqués ensemble (bouton « Appliquer ») pour éviter un recalcul à chaque saisie.
 */
@Component({
  selector: 'app-filter-bar',
  standalone: true,
  imports: [FormsModule],
  template: `
    <form class="filter-bar" (ngSubmit)="apply()" aria-label="Filtres des indicateurs">
      <label>
        <span>Complétées du</span>
        <input type="date" name="from" [(ngModel)]="draft.from" [max]="draft.to || null" />
      </label>
      <label>
        <span>au</span>
        <input type="date" name="to" [(ngModel)]="draft.to" [min]="draft.from || null" />
      </label>
      <label>
        <span>Équipe</span>
        <select name="supervisor" [(ngModel)]="draft.supervisor">
          <option [ngValue]="null">Toutes</option>
          @for (supervisor of options?.supervisors ?? []; track supervisor) {
            <option [ngValue]="supervisor">{{ supervisor }}</option>
          }
        </select>
      </label>
      <label>
        <span>Enquêteur</span>
        <select name="interviewer" [(ngModel)]="draft.interviewer">
          <option [ngValue]="null">Tous</option>
          @for (interviewer of options?.interviewers ?? []; track interviewer) {
            <option [ngValue]="interviewer">{{ interviewer }}</option>
          }
        </select>
      </label>
      <label>
        <span>Statut</span>
        <select name="status" [(ngModel)]="draft.status">
          <option [ngValue]="null">Terminées et approuvées</option>
          <option value="ALL">Tous les statuts</option>
          @for (status of options?.statuses ?? []; track status) {
            <option [ngValue]="status">{{ statusLabel(status) }}</option>
          }
        </select>
      </label>
      <label>
        <span>Zone / ventilation</span>
        <select name="variable" [ngModel]="draft.variable" (ngModelChange)="selectVariable($event)">
          <option [ngValue]="null">Aucune</option>
          @for (question of variables; track question.variableName) {
            <option [ngValue]="question.variableName">
              {{ question.label || question.questionText || question.variableName }}
            </option>
          }
        </select>
      </label>
      @if (draft.variable) {
        <label>
          <span>Valeur</span>
          <select name="value" [(ngModel)]="draft.value" [disabled]="!variableOptions.length">
            <option [ngValue]="null">Choisir…</option>
            @for (option of variableOptions; track option.answer) {
              <option [ngValue]="'' + option.answer">{{ option.text }}</option>
            }
          </select>
        </label>
      }
      <div class="filter-actions">
        <button type="submit" class="export-button filter-apply" [disabled]="!!draft.variable && !draft.value">
          Appliquer
        </button>
        @if (active) {
          <button type="button" class="filter-reset" (click)="reset()">Réinitialiser</button>
        }
      </div>
    </form>
  `
})
export class FilterBarComponent implements OnChanges {
  @Input() filters: AnalysisFilters = NO_FILTERS;
  @Input() options: FilterOptions | null = null;
  /** Questions à choix unique proposées comme variables de ventilation (ex. région). */
  @Input() questions: QuestionSummary[] = [];
  @Input() variableOptions: QuestionOption[] = [];
  @Input() statusLabel: (status: string) => string = (status) => status;
  @Output() readonly filtersChange = new EventEmitter<AnalysisFilters>();
  @Output() readonly variableSelected = new EventEmitter<string | null>();

  protected draft: AnalysisFilters = { ...NO_FILTERS };

  protected get variables(): QuestionSummary[] {
    return this.questions.filter((question) => {
      const type = (question.type ?? '').toLowerCase();
      return question.optionCount > 0 && type.includes('single');
    });
  }

  protected get active(): boolean {
    return Object.values(this.filters).some((value) => value !== null);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['filters']) {
      this.draft = { ...this.filters };
    }
  }

  protected selectVariable(variable: string | null): void {
    this.draft = { ...this.draft, variable, value: null };
    this.variableSelected.emit(variable);
  }

  protected apply(): void {
    this.filtersChange.emit({
      ...this.draft,
      from: this.draft.from || null,
      to: this.draft.to || null
    });
  }

  protected reset(): void {
    this.draft = { ...NO_FILTERS };
    this.variableSelected.emit(null);
    this.filtersChange.emit({ ...NO_FILTERS });
  }
}
