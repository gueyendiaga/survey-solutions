import { Component, ElementRef, HostListener, Input, forwardRef } from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR } from '@angular/forms';

export interface SearchSelectOption {
  value: string | null;
  label: string;
}

@Component({
  selector: 'app-search-select',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './search-select.html',
  styleUrl: './search-select.css',
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => SearchSelectComponent),
    multi: true
  }]
})
export class SearchSelectComponent implements ControlValueAccessor {
  @Input() options: SearchSelectOption[] = [];
  @Input() ariaLabel = 'Choisir une option';
  @Input() placeholder = 'Choisir…';
  @Input() disabled = false;

  protected value: string | null = null;
  protected query = '';
  protected open = false;

  private onChange: (value: string | null) => void = () => {};
  private onTouched: () => void = () => {};

  constructor(private readonly element: ElementRef<HTMLElement>) {}

  protected get selectedLabel(): string {
    return this.options.find((option) => option.value === this.value)?.label ?? this.placeholder;
  }

  protected get filteredOptions(): SearchSelectOption[] {
    const query = this.query.trim().toLocaleLowerCase('fr');
    return query
      ? this.options.filter((option) => option.label.toLocaleLowerCase('fr').includes(query))
      : this.options;
  }

  writeValue(value: string | null): void {
    this.value = value;
  }

  registerOnChange(fn: (value: string | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.disabled = disabled;
  }

  protected toggle(): void {
    if (this.disabled) {
      return;
    }
    this.open = !this.open;
    this.query = '';
    this.onTouched();
  }

  protected choose(option: SearchSelectOption): void {
    this.value = option.value;
    this.onChange(option.value);
    this.onTouched();
    this.open = false;
    this.query = '';
  }

  protected closeOnEscape(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
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
}
