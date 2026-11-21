import { DecimalPipe } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

/**
 * Pied de tableau paginé : « Affichage de 1 à 10 sur N », Précédent / Page X sur Y / Suivant.
 * La page est numérotée à partir de 1.
 */
@Component({
  selector: 'app-table-pager',
  standalone: true,
  imports: [DecimalPipe],
  template: `
    <footer class="ds-pager">
      <span class="ds-pager-range">
        Affichage de {{ start() | number: '1.0-0' : 'fr-FR' }} à {{ end() | number: '1.0-0' : 'fr-FR' }}
        sur {{ total | number: '1.0-0' : 'fr-FR' }} {{ unit }}
      </span>
      @if (pageCount() > 1) {
        <nav class="ds-pager-nav" aria-label="Pagination">
          <button type="button" class="ds-pager-btn" aria-label="Page précédente" [disabled]="disabled || page <= 1" (click)="go(page - 1)">Précédent</button>
          <span class="ds-pager-page">Page {{ page }} sur {{ pageCount() }}</span>
          <button type="button" class="ds-pager-btn" aria-label="Page suivante" [disabled]="disabled || page >= pageCount()" (click)="go(page + 1)">Suivant</button>
        </nav>
      }
    </footer>
  `
})
export class TablePagerComponent {
  @Input({ required: true }) page = 1;
  @Input({ required: true }) pageSize = 10;
  @Input({ required: true }) total = 0;
  /** Nom des lignes au pluriel (« structures », « enquêteurs »…). */
  @Input() unit = 'lignes';
  @Input() disabled = false;
  @Output() readonly pageChange = new EventEmitter<number>();

  protected pageCount(): number {
    return Math.max(1, Math.ceil(this.total / this.pageSize));
  }

  protected start(): number {
    return this.total ? (this.page - 1) * this.pageSize + 1 : 0;
  }

  protected end(): number {
    return Math.min(this.page * this.pageSize, this.total);
  }

  protected go(page: number): void {
    if (page >= 1 && page <= this.pageCount() && page !== this.page) {
      this.pageChange.emit(page);
    }
  }
}
