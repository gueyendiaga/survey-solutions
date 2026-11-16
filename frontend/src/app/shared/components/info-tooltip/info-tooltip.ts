import { Component, Input } from '@angular/core';

let nextTooltipId = 0;

/** Petite icône d’aide avec une infobulle accessible au survol et au clavier. */
@Component({
  selector: 'app-info-tooltip',
  standalone: true,
  templateUrl: './info-tooltip.html',
  styleUrl: './info-tooltip.css'
})
export class InfoTooltipComponent {
  @Input({ required: true }) text = '';

  protected readonly tooltipId = `info-tooltip-${nextTooltipId++}`;
  protected visible = false;
  protected left = 0;
  protected top = 0;
  protected placement: 'above' | 'below' = 'above';

  protected show(event: Event): void {
    const trigger = event.currentTarget as HTMLElement;
    const bounds = trigger.getBoundingClientRect();
    const tooltipWidth = Math.min(300, window.innerWidth - 24);
    this.left = Math.max(12, Math.min(
      bounds.left + bounds.width / 2 - tooltipWidth / 2,
      window.innerWidth - tooltipWidth - 12
    ));
    this.placement = bounds.top > 120 ? 'above' : 'below';
    this.top = this.placement === 'above' ? bounds.top - 8 : bounds.bottom + 8;
    this.visible = true;
  }

  protected hide(): void {
    this.visible = false;
  }
}
