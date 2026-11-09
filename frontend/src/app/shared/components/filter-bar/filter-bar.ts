import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AnalysisFilters,
  FilterOptions,
  NO_FILTERS,
  QuestionOption,
  QuestionSummary
} from '../../../core/models/dashboard.models';

/**
 * Filtres communs au suivi terrain, aux variables, à la qualité et aux rapports. Des pastilles
 * résument les filtres en vigueur ; un clic ouvre le formulaire, appliqué d'un seul coup pour
 * éviter un recalcul à chaque saisie.
 */
@Component({
  selector: 'app-filter-bar',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './filter-bar.html',
  styleUrl: './filter-bar.css'
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
