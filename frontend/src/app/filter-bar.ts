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
 * Filtres communs au suivi terrain, aux variables, à la qualité et aux rapports. Des pastilles
 * résument les filtres en vigueur ; un clic ouvre le formulaire, appliqué d'un seul coup pour
 * éviter un recalcul à chaque saisie.
 */
@Component({
  selector: 'app-filter-bar',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="filter-chips" role="group" aria-label="Filtres">
      <button type="button" class="chip" [class.set]="!!filters.from || !!filters.to" [attr.aria-expanded]="open" (click)="toggle()">
        <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M3 10h18M8 3v4M16 3v4" /></svg>
        {{ periodLabel() }}
      </button>
      <button type="button" class="chip" [class.set]="!!filters.supervisor" (click)="toggle()">Équipe : {{ filters.supervisor || 'toutes' }}</button>
      <button type="button" class="chip" [class.set]="!!filters.interviewer" (click)="toggle()">Enquêteur : {{ filters.interviewer || 'tous' }}</button>
      <button type="button" class="chip" [class.set]="!!filters.status" (click)="toggle()">{{ statusSummary() }}</button>
      <button type="button" class="chip" [class.set]="!!filters.variable" [class.dashed]="!filters.variable" (click)="toggle()">
        @if (filters.variable) {
          Zone : {{ filters.variable }} = {{ filters.value }}
        } @else {
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v14M5 12h14" /></svg>
          Zone
        }
      </button>
      @if (active) {
        <button type="button" class="chip ghost filter-reset" (click)="reset()">Réinitialiser</button>
      }
    </div>

    @if (open) {
      <form class="filter-bar" (ngSubmit)="apply()" aria-label="Filtres des indicateurs">
        <label>
          <span>Terminées du</span>
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
            <option [ngValue]="null">Terminées et approuvées (défaut)</option>
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
          <button type="button" class="btn ghost" (click)="open = false">Annuler</button>
          <button type="submit" class="btn primary filter-apply" [disabled]="!!draft.variable && !draft.value">Appliquer</button>
        </div>
      </form>
    }
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
  protected open = false;

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

  protected toggle(): void {
    this.open = !this.open;
    if (this.open) {
      this.draft = { ...this.filters };
    }
  }

  protected periodLabel(): string {
    const day = (value: string) =>
      new Date(`${value}T00:00:00Z`).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', timeZone: 'UTC' });
    const { from, to } = this.filters;
    if (from && to) {
      return `Du ${day(from)} au ${day(to)}`;
    }
    if (from) {
      return `Depuis le ${day(from)}`;
    }
    return to ? `Jusqu’au ${day(to)}` : 'Toutes les dates';
  }

  protected statusSummary(): string {
    const status = this.filters.status;
    if (!status) {
      return 'Terminées et approuvées';
    }
    return status === 'ALL' ? 'Tous les statuts' : this.statusLabel(status);
  }

  protected selectVariable(variable: string | null): void {
    this.draft = { ...this.draft, variable, value: null };
    this.variableSelected.emit(variable);
  }

  protected apply(): void {
    this.open = false;
    this.filtersChange.emit({
      ...this.draft,
      from: this.draft.from || null,
      to: this.draft.to || null
    });
  }

  protected reset(): void {
    this.open = false;
    this.draft = { ...NO_FILTERS };
    this.variableSelected.emit(null);
    this.filtersChange.emit({ ...NO_FILTERS });
  }
}
