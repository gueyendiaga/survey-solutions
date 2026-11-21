import { Component, EventEmitter, Input, Output } from '@angular/core';

/** Champ de recherche d'un tableau, avec icône loupe (coin supérieur droit des cartes). */
@Component({
  selector: 'app-table-search',
  standalone: true,
  host: { class: 'ds-search' },
  template: `
    <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
    <input
      type="search"
      [attr.aria-label]="label || placeholder"
      [placeholder]="placeholder"
      [value]="value"
      (input)="valueChange.emit($any($event.target).value)" />
  `
})
export class TableSearchComponent {
  @Input() value = '';
  @Input() placeholder = 'Rechercher…';
  @Input() label = '';
  @Output() readonly valueChange = new EventEmitter<string>();
}
