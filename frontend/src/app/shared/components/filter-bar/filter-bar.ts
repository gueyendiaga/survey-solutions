import {
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  ViewChild
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SearchSelectComponent, SearchSelectOption } from '../search-select/search-select';
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
  imports: [FormsModule, SearchSelectComponent],
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

  @ViewChild('filterTrigger') private filterTrigger?: ElementRef<HTMLButtonElement>;
  @ViewChild('filterDialog') private filterDialog?: ElementRef<HTMLElement>;

  protected draft: AnalysisFilters = { ...NO_FILTERS };
  protected open = false;

  protected get variables(): QuestionSummary[] {
    return this.questions.filter((question) => {
      const type = (question.type ?? '').toLowerCase();
      return question.optionCount > 0 && type.includes('single');
    });
  }

  protected get supervisorOptions(): SearchSelectOption[] {
    return [
      { value: null, label: 'Toutes' },
      ...(this.options?.supervisors ?? []).map((value) => ({ value, label: value }))
    ];
  }

  protected get interviewerOptions(): SearchSelectOption[] {
    return [
      { value: null, label: 'Tous' },
      ...(this.options?.interviewers ?? []).map((value) => ({ value, label: value }))
    ];
  }

  protected get statusOptions(): SearchSelectOption[] {
    return [
      { value: null, label: 'Terminées et approuvées (défaut)' },
      { value: 'ALL', label: 'Tous les statuts' },
      ...(this.options?.statuses ?? []).map((value) => ({ value, label: this.statusLabel(value) }))
    ];
  }

  protected get variableSelectOptions(): SearchSelectOption[] {
    return [
      { value: null, label: 'Aucune' },
      ...this.variables.map((question) => ({
        value: question.variableName,
        label: question.label || question.questionText || question.variableName
      }))
    ];
  }

  protected get valueSelectOptions(): SearchSelectOption[] {
    return [
      { value: null, label: 'Choisir…' },
      ...this.variableOptions.map((option) => ({ value: `${option.answer}`, label: option.text }))
    ];
  }

  protected get active(): boolean {
    return Object.values(this.filters).some((value) => value !== null);
  }

  protected get activeCount(): number {
    return this.activeSummaries.length;
  }

  protected get activeSummaries(): string[] {
    const summaries: string[] = [];
    if (this.filters.from || this.filters.to) summaries.push(this.periodLabel());
    if (this.filters.supervisor) summaries.push(`Équipe : ${this.filters.supervisor}`);
    if (this.filters.interviewer) summaries.push(`Enquêteur : ${this.filters.interviewer}`);
    if (this.filters.status) summaries.push(this.statusSummary());
    if (this.filters.variable) {
      summaries.push(`Zone : ${this.filters.variable}${this.filters.value ? ` = ${this.filters.value}` : ''}`);
    }
    return summaries;
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['filters']) {
      this.draft = { ...this.filters };
    }
  }

  protected toggle(): void {
    if (this.open) {
      this.close();
      return;
    }

    this.draft = { ...this.filters };
    this.open = true;
    requestAnimationFrame(() => this.filterDialog?.nativeElement.focus());
  }

  protected close(): void {
    this.open = false;
    requestAnimationFrame(() => this.filterTrigger?.nativeElement.focus());
  }

  protected onBackdropClick(event: MouseEvent): void {
    if (event.target === event.currentTarget) this.close();
  }

  protected trapFocus(event: KeyboardEvent): void {
    if (event.key !== 'Tab' || !this.filterDialog) return;

    const dialog = this.filterDialog.nativeElement;
    const focusable = [...dialog.querySelectorAll<HTMLElement>(
      'button:not(:disabled), input:not(:disabled), [tabindex]:not([tabindex="-1"])'
    )];
    if (!focusable.length) return;

    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    const activeElement = document.activeElement;
    if (event.shiftKey && (activeElement === first || activeElement === dialog)) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && (activeElement === last || activeElement === dialog)) {
      event.preventDefault();
      first.focus();
    }
  }

  protected clearDraft(): void {
    const hadVariable = !!this.draft.variable;
    this.draft = { ...NO_FILTERS };
    if (hadVariable) this.variableSelected.emit(null);
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    if (this.open) this.close();
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
    requestAnimationFrame(() => this.filterTrigger?.nativeElement.focus());
  }

  protected reset(): void {
    this.close();
    this.clearDraft();
    this.filtersChange.emit({ ...NO_FILTERS });
  }
}
