import { Component, ElementRef, HostListener, Input, forwardRef, inject } from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR } from '@angular/forms';
import { SearchSelectOption } from '../search-select/search-select';
import { htmlToText } from '../../utils/html-text';

let nextId = 0;

/**
 * Sélection multiple avec recherche : liste à cases à cocher qui reste ouverte pendant la
 * sélection, et puces retirables pour les éléments choisis. Valeur : tableau de `value`.
 */
@Component({
  selector: 'app-multi-search-select',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './multi-search-select.html',
  styleUrl: './multi-search-select.css',
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => MultiSearchSelectComponent),
    multi: true
  }]
})
export class MultiSearchSelectComponent implements ControlValueAccessor {
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);

  @Input() options: SearchSelectOption[] = [];
  @Input() ariaLabel = 'Choisir des options';
  @Input() placeholder = 'Choisir…';
  /** Nom des éléments au pluriel, pour le résumé : « 3 questions sélectionnées ». */
  @Input() itemsLabel = 'éléments';
  @Input() disabled = false;
  /** Compteur facultatif affiché sur chaque puce (ex. : interviews concernées). */
  @Input() counts: Record<string, number> | null = null;

  protected readonly listId = `multi-select-options-${nextId++}`;
  protected values: string[] = [];
  protected query = '';
  protected open = false;

  private onChange: (value: string[]) => void = () => {};
  private onTouched: () => void = () => {};

  protected get filteredOptions(): SearchSelectOption[] {
    const query = this.query.trim().toLocaleLowerCase('fr');
    return query
      ? this.options.filter((option) => htmlToText(option.label).toLocaleLowerCase('fr').includes(query))
      : this.options;
  }

  /** Puces dans l'ordre de sélection ; une valeur absente des options reste affichée telle quelle. */
  protected get selected(): SearchSelectOption[] {
    return this.values.map((value) => this.options.find((option) => option.value === value) ?? { value, label: value });
  }

  protected get summary(): string {
    const count = this.values.length;
    return count ? `${count} ${this.itemsLabel} sélectionné${count > 1 ? 's' : ''}` : this.placeholder;
  }

  writeValue(value: string[] | null): void {
    this.values = [...(value ?? [])];
  }

  registerOnChange(fn: (value: string[]) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.disabled = disabled;
  }

  protected isSelected(option: SearchSelectOption): boolean {
    return option.value !== null && this.values.includes(option.value);
  }

  protected toggleOpen(): void {
    if (this.disabled) {
      return;
    }
    this.open = !this.open;
    this.query = '';
    this.onTouched();
  }

  protected toggle(option: SearchSelectOption): void {
    if (option.value === null) {
      return;
    }
    this.emit(this.isSelected(option)
      ? this.values.filter((value) => value !== option.value)
      : [...this.values, option.value]);
  }

  protected remove(value: string | null): void {
    this.emit(this.values.filter((item) => item !== value));
  }

  /** Ajoute toutes les options visibles (résultat de la recherche courante). */
  protected selectVisible(): void {
    const additions = this.filteredOptions
      .map((option) => option.value)
      .filter((value): value is string => value !== null && !this.values.includes(value));
    this.emit([...this.values, ...additions]);
  }

  protected clear(): void {
    this.emit([]);
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape' && this.open) {
      event.stopPropagation();
      this.open = false;
      this.query = '';
    }
  }

  @HostListener('document:click', ['$event'])
  protected closeOutside(event: MouseEvent): void {
    if (!this.element.nativeElement.contains(event.target as Node)) {
      this.open = false;
      this.query = '';
    }
  }

  private emit(values: string[]): void {
    this.values = values;
    this.onChange([...values]);
    this.onTouched();
  }
}
