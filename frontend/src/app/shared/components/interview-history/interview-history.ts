import { Component, ElementRef, EventEmitter, HostListener, Input, OnChanges, Output, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DashboardApiService } from '../../../core/services/dashboard-api.service';
import { LoadingStateComponent } from '../loading-state/loading-state';
import { apiErrorMessage, saveFile } from '../../utils/http-utils';
import {
  HistoryEntry,
  HistoryFilter,
  HistoryModel,
  buildHistory,
  formatDuration,
  historyCsv,
  visibleEntries
} from './interview-history.utils';

interface DayGroup {
  day: string;
  entries: HistoryEntry[];
}

const FILTERS: { id: HistoryFilter; label: string; hint: string }[] = [
  { id: 'workflow', label: 'Parcours', hint: 'Création, affectations, validations, rejets, commentaires' },
  { id: 'answer', label: 'Réponses', hint: 'Saisies, corrections et effacements' },
  { id: 'session', label: 'Sessions', hint: 'Reprises et interruptions de saisie' },
  { id: 'system', label: 'Système', hint: 'Validations et variables recalculées' },
  { id: 'all', label: 'Tout', hint: 'Journal complet' }
];

/** Panneau latéral : historique complet d'une interview (journal HQ), filtrable et exportable. */
@Component({
  selector: 'app-interview-history',
  standalone: true,
  imports: [FormsModule, LoadingStateComponent],
  templateUrl: './interview-history.html',
  styleUrl: './interview-history.css'
})
export class InterviewHistoryComponent implements OnChanges {
  private readonly api = inject(DashboardApiService);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);

  @Input({ required: true }) interviewId!: string;
  @Input() interviewKey: string | null = null;
  @Input() statusLabel: string | null = null;
  @Output() readonly closed = new EventEmitter<void>();

  protected readonly filters = FILTERS;
  protected readonly formatDuration = formatDuration;
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly model = signal<HistoryModel | null>(null);
  protected readonly filter = signal<HistoryFilter>('workflow');
  protected readonly search = signal('');
  protected readonly correctionsOnly = signal(false);

  protected readonly entries = computed(() => {
    const model = this.model();
    return model ? visibleEntries(model.entries, this.filter(), this.search(), this.correctionsOnly()) : [];
  });

  protected readonly days = computed<DayGroup[]>(() => {
    const groups: DayGroup[] = [];
    for (const entry of this.entries()) {
      const day = entry.at
        ? entry.at.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
        : 'Date inconnue';
      const last = groups[groups.length - 1];
      if (last?.day === day) {
        last.entries.push(entry);
      } else {
        groups.push({ day, entries: [entry] });
      }
    }
    return groups;
  });

  ngOnChanges(): void {
    this.load();
    queueMicrotask(() => this.element.nativeElement.querySelector<HTMLElement>('.ih-close')?.focus());
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    const requested = this.interviewId;
    this.api.interviewHistory(requested).subscribe({
      next: (history) => {
        if (requested === this.interviewId) {
          this.model.set(buildHistory(history.records ?? []));
          this.loading.set(false);
        }
      },
      error: (error: unknown) => {
        if (requested === this.interviewId) {
          this.error.set(apiErrorMessage(error, 'L’historique de cette interview n’est pas disponible.'));
          this.loading.set(false);
        }
      }
    });
  }

  protected setFilter(filter: HistoryFilter): void {
    this.filter.set(filter);
    if (filter !== 'answer') {
      this.correctionsOnly.set(false);
    }
  }

  protected time(entry: HistoryEntry): string {
    return entry.at ? entry.at.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : '—';
  }

  protected shortDate(date: Date | null): string {
    return date
      ? date.toLocaleString('fr-FR', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })
      : '—';
  }

  protected exportCsv(): void {
    const model = this.model();
    if (!model) {
      return;
    }
    const key = (this.interviewKey ?? this.interviewId).replace(/[^a-zA-Z0-9._-]+/g, '-');
    saveFile(new Blob([historyCsv(model.entries)], { type: 'text/csv;charset=utf-8' }), `historique-${key}.csv`);
  }

  @HostListener('document:keydown.escape')
  protected close(): void {
    this.closed.emit();
  }
}
