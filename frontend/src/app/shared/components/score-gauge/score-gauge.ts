import { Component, Input } from '@angular/core';

/** Jauge circulaire d'un score sur 100 (ou d'un pourcentage), colorée selon le niveau. */
@Component({
  selector: 'app-score-gauge',
  standalone: true,
  template: `
    <svg [attr.width]="size" [attr.height]="size" viewBox="0 0 120 120" role="img" [attr.aria-label]="label + ' : ' + display()">
      <circle cx="60" cy="60" r="50" fill="none" stroke="#e5e7eb" stroke-width="10" />
      <circle cx="60" cy="60" r="50" fill="none" [attr.stroke]="color()" stroke-width="10" stroke-linecap="round"
        [attr.stroke-dasharray]="dash()" transform="rotate(-90 60 60)" />
      <text x="60" y="60" text-anchor="middle" class="gauge-value">{{ display() }}</text>
      <text x="60" y="80" text-anchor="middle" class="gauge-unit">{{ unit }}</text>
    </svg>
  `,
  styles: `
    :host { display: inline-flex; }
    .gauge-value { fill: var(--text-strong); font-size: 26px; font-weight: 800; letter-spacing: -0.03em; }
    .gauge-unit { fill: var(--text-2); font-size: 12px; }
  `
})
export class ScoreGaugeComponent {
  /** Valeur 0-100 ; null : jauge vide. */
  @Input() value: number | null = null;
  @Input() unit = '/100';
  /** Texte collé à la valeur (« % »). */
  @Input() suffix = '';
  @Input() label = 'Score';
  @Input() size = 136;

  private readonly circumference = 2 * Math.PI * 50;

  protected display(): string {
    return this.value === null ? '—' : `${Math.round(this.value).toLocaleString('fr-FR')}${this.suffix}`;
  }

  protected dash(): string {
    const ratio = Math.max(0, Math.min(100, this.value ?? 0)) / 100;
    return `${ratio * this.circumference} ${this.circumference}`;
  }

  /** Rouge sous 40, orange sous 70, vert au-delà. */
  protected color(): string {
    const value = this.value ?? 0;
    return value < 40 ? '#ef4444' : value < 70 ? '#f59e0b' : '#10b981';
  }
}
