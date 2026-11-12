import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, ElementRef, EventEmitter, HostListener, Input, Output, QueryList, ViewChildren } from '@angular/core';
import { Questionnaire, SyncStatus } from '../../../core/models/dashboard.models';
import { QUESTIONNAIRE_TABS, QuestionnaireTab, TabBadges } from '../questionnaire-tabs';

export type { QuestionnaireTab, TabBadges } from '../questionnaire-tabs';

/**
 * En-tête du questionnaire ouvert : identité, fraîcheur des données, actions et onglets.
 * Les onglets suivent le motif ARIA « tabs » à activation manuelle : les flèches déplacent le
 * focus, Entrée ou Espace activent l'onglet (chaque onglet charge des données).
 */
@Component({
  selector: 'app-questionnaire-header',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
  templateUrl: './questionnaire-header.html',
  styleUrl: './questionnaire-header.css'
})
export class QuestionnaireHeaderComponent {
  @Input({ required: true }) questionnaire!: Questionnaire;
  @Input({ required: true }) set active(value: QuestionnaireTab) {
    this.activeTab = value;
    this.focusIndex = Math.max(0, this.tabs.findIndex((tab) => tab.id === value));
  }
  get active(): QuestionnaireTab {
    return this.activeTab;
  }
  @Input() syncStatus: SyncStatus | null = null;
  @Input() syncRequesting = false;
  @Input() exporting = false;
  @Input() reportGenerating = false;
  @Input() badges: TabBadges = { variables: null, quality: null, reports: null };

  @Output() readonly tabChange = new EventEmitter<QuestionnaireTab>();
  @Output() readonly changeQuestionnaire = new EventEmitter<Event>();
  @Output() readonly sync = new EventEmitter<void>();
  @Output() readonly exportData = new EventEmitter<'csv' | 'xlsx'>();
  @Output() readonly report = new EventEmitter<void>();

  @ViewChildren('tabButton') private tabButtons!: QueryList<ElementRef<HTMLButtonElement>>;

  protected readonly tabs = QUESTIONNAIRE_TABS;
  protected focusIndex = 0;
  protected menuOpen = false;
  private activeTab: QuestionnaireTab = 'fieldwork';

  constructor(private readonly host: ElementRef<HTMLElement>) {}

  /** Ferme le menu d'export en cliquant ailleurs ou avec Échap. */
  @HostListener('document:click', ['$event'])
  protected closeMenuOutside(event: MouseEvent): void {
    if (this.menuOpen && !this.host.nativeElement.querySelector('.menu')?.contains(event.target as Node)) {
      this.menuOpen = false;
    }
  }

  @HostListener('document:keydown.escape')
  protected closeMenu(): void {
    this.menuOpen = false;
  }

  protected select(tab: QuestionnaireTab): void {
    this.menuOpen = false;
    if (tab !== this.activeTab) {
      this.tabChange.emit(tab);
    }
  }

  protected onChange(event: Event): void {
    event.preventDefault();
    this.changeQuestionnaire.emit(event);
  }

  protected exportAs(format: 'csv' | 'xlsx'): void {
    this.menuOpen = false;
    this.exportData.emit(format);
  }

  /** Navigation clavier entre onglets : flèches, Début, Fin (activation par Entrée ou Espace). */
  protected onKeydown(event: KeyboardEvent): void {
    const last = this.tabs.length - 1;
    const moves: Record<string, number> = {
      ArrowRight: this.focusIndex === last ? 0 : this.focusIndex + 1,
      ArrowLeft: this.focusIndex === 0 ? last : this.focusIndex - 1,
      Home: 0,
      End: last
    };
    if (!(event.key in moves)) {
      return;
    }
    event.preventDefault();
    this.focusIndex = moves[event.key];
    this.tabButtons.get(this.focusIndex)?.nativeElement.focus();
  }

  protected badge(tab: QuestionnaireTab): number | null {
    return tab === 'fieldwork' ? null : this.badges[tab] || null;
  }

  protected badgeLabel(tab: QuestionnaireTab, count: number): string {
    return tab === 'variables' ? `${count} variables`
      : tab === 'quality' ? `${count} variable(s) à examiner`
      : `${count} rapport(s) archivé(s)`;
  }
}
