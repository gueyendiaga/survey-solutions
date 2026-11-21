import { Component, Input } from '@angular/core';

export type KpiTone = 'blue' | 'green' | 'violet' | 'orange' | 'red' | 'cyan';

/**
 * Carte de chiffre clé en dégradé pastel : titre coloré, grand chiffre, sous-titre,
 * barre de progression (progress, 0-100) ou pastille. Contenu projeté :
 * [kpiIcon] remplace la pastille en haut à droite ; le reste s'affiche sous le sous-titre.
 */
@Component({
  selector: 'app-kpi-card',
  standalone: true,
  host: { '[class]': "'ds-kpi ' + tone" },
  template: `
    <div class="ds-kpi-top">
      <span class="ds-kpi-label">{{ label }}<ng-content select="[kpiLabel]" /></span>
      <ng-content select="[kpiIcon]">
        <span class="ds-kpi-marker" aria-hidden="true"></span>
      </ng-content>
    </div>
    <strong class="ds-kpi-value">{{ value }}<ng-content select="[kpiValue]" /></strong>
    @if (sub) {
      <span class="ds-kpi-sub">{{ sub }}</span>
    }
    <ng-content />
    @if (progress !== null && progress !== undefined) {
      <span class="ds-progress" role="img" [attr.aria-label]="label + ' : ' + clamp(progress) + ' %'">
        <span [style.width.%]="clamp(progress)"></span>
      </span>
    }
  `
})
export class KpiCardComponent {
  @Input() tone: KpiTone = 'blue';
  @Input({ required: true }) label = '';
  @Input() value: string | number = '—';
  @Input() sub = '';
  /** Remplissage de la petite barre de progression (0-100) ; null : pas de barre. */
  @Input() progress: number | null = null;

  protected clamp(value: number): number {
    return Math.max(0, Math.min(100, value));
  }
}
