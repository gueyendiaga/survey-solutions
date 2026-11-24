import { ChangeDetectorRef, Component, ElementRef, Input, NgZone, OnDestroy, ViewChild, inject } from '@angular/core';
import { TooltipPosition, tooltipPosition } from './tooltip-position';

let nextTooltipId = 0;

/** Petite icône d’aide avec une infobulle accessible au survol et au clavier. */
@Component({
  selector: 'app-info-tooltip',
  standalone: true,
  templateUrl: './info-tooltip.html',
  styleUrl: './info-tooltip.css'
})
export class InfoTooltipComponent implements OnDestroy {
  @Input({ required: true }) text = '';

  @ViewChild('trigger', { static: true }) private readonly trigger!: ElementRef<HTMLElement>;
  @ViewChild('bubble') private readonly bubble?: ElementRef<HTMLElement>;

  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly zone = inject(NgZone);
  private listeners?: AbortController;

  protected readonly tooltipId = `info-tooltip-${nextTooltipId++}`;
  protected visible = false;
  protected position: TooltipPosition = { side: 'right', left: 0, top: 0, arrow: 0 };

  protected show(): void {
    if (this.visible) return;
    this.visible = true;
    // La bulle doit être rendue pour connaître sa taille réelle avant de la placer.
    this.changeDetector.detectChanges();
    const bubble = this.bubble?.nativeElement;
    if (!bubble) return;
    // Couche supérieure : ni le transform des cartes ni un overflow ne décalent la bulle.
    bubble.showPopover?.();
    this.position = tooltipPosition(
      this.trigger.nativeElement.getBoundingClientRect(),
      { width: bubble.offsetWidth, height: bubble.offsetHeight },
      { width: document.documentElement.clientWidth, height: window.innerHeight }
    );
    this.changeDetector.detectChanges();
    this.closeOnMove();
  }

  protected hide(): void {
    this.listeners?.abort();
    this.visible = false;
  }

  ngOnDestroy(): void {
    this.listeners?.abort();
  }

  /** Ferme la bulle dès que l’icône peut bouger ; les écouteurs n’existent que le temps de l’affichage. */
  private closeOnMove(): void {
    this.listeners = new AbortController();
    const { signal } = this.listeners;
    const close = () => this.zone.run(() => {
      this.hide();
      this.changeDetector.detectChanges();
    });
    this.zone.runOutsideAngular(() => {
      // Capture : le défilement d’un conteneur interne ne remonte pas jusqu’à la fenêtre.
      window.addEventListener('scroll', close, { capture: true, passive: true, signal });
      window.addEventListener('resize', close, { signal });
      document.addEventListener('keydown', event => {
        if (event.key === 'Escape') close();
      }, { signal });
    });
  }
}
