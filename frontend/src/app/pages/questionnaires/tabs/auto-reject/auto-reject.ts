import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AutoRejectEvent,
  AutoRejectMode,
  AutoRejectPreview,
  AutoRejectRuleType,
  AutoRejectSettings
} from '../../../../core/models/dashboard.models';
import { DashboardApiService } from '../../../../core/services/dashboard-api.service';
import { SearchSelectComponent, SearchSelectOption } from '../../../../shared/components/search-select/search-select';
import { MultiSearchSelectComponent } from '../../../../shared/components/multi-search-select/multi-search-select';
import { apiErrorMessage } from '../../../../shared/utils/http-utils';
import { TablePagerComponent } from '../../../../shared/components/table-pager/table-pager';
import { TableSearchComponent } from '../../../../shared/components/table-search/table-search';
import { createTableState } from '../../../../shared/utils/table-state';
import { QuestionnaireStore } from '../../questionnaire.store';
import {
  GLOBAL_TYPES,
  RangeItem,
  RuleCard,
  cardProblem,
  cardsFromRules,
  emptyCard,
  rulesFromCards,
  rulesSignature
} from './rule-cards';

interface RuleTemplate {
  type: AutoRejectRuleType;
  label: string;
  description: string;
  defaults: Partial<RuleCard>;
}

export const RULE_TEMPLATES: RuleTemplate[] = [
  { type: 'VALIDATION_ERRORS', label: 'Erreurs de validation', description: 'Au moins N erreurs de validation non résolues.', defaults: { minValue: 1 } },
  { type: 'MIN_DURATION', label: 'Durée trop courte', description: 'Interview saisie en moins de N minutes.', defaults: { minValue: 15 } },
  { type: 'MAX_UNANSWERED', label: 'Trop de non-réponses', description: 'Plus de N questions actives laissées sans réponse.', defaults: { maxValue: 5 } },
  { type: 'REQUIRED_ANSWER', label: 'Réponse obligatoire', description: 'Une ou plusieurs questions actives laissées sans réponse.', defaults: {} },
  { type: 'VALUE_RANGE', label: 'Valeur hors plage', description: 'Valeurs numériques hors des bornes attendues, question par question.', defaults: {} }
];

const MODES: { id: AutoRejectMode; label: string; help: string }[] = [
  { id: 'OFF', label: 'Désactivé', help: 'Les règles sont conservées mais ne sont pas évaluées.' },
  { id: 'SIMULATION', label: 'Simulation', help: 'Les règles signalent les interviews concernées ; aucune n’est rejetée.' },
  { id: 'AUTO', label: 'Automatique', help: 'Après chaque synchronisation, HQ rejette les interviews en infraction avec un commentaire.' }
];

/** Paramétrage des rejets automatiques d'une version de questionnaire, avec simulation préalable. */
@Component({
  selector: 'app-auto-reject',
  standalone: true,
  imports: [FormsModule, SearchSelectComponent, MultiSearchSelectComponent, TablePagerComponent, TableSearchComponent],
  templateUrl: './auto-reject.html',
  styleUrl: './auto-reject.css'
})
export class AutoRejectComponent {
  private readonly api = inject(DashboardApiService);
  protected readonly store = inject(QuestionnaireStore);
  protected readonly templates = RULE_TEMPLATES;
  protected readonly modes = MODES;

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly saved = signal<AutoRejectSettings | null>(null);
  protected readonly events = signal<AutoRejectEvent[]>([]);
  protected readonly mode = signal<AutoRejectMode>('OFF');
  protected readonly maxHqRejections = signal(2);
  /** Une carte par type de règle (voir rule-cards.ts). */
  protected readonly cards = signal<RuleCard[]>([]);
  protected readonly autoConfirmed = signal(false);
  protected readonly preview = signal<AutoRejectPreview | null>(null);
  protected readonly previewing = signal(false);
  /** Interviews en infraction de la dernière simulation, paginées. */
  protected readonly candidates = createTableState(
    () => this.preview()?.candidates ?? [],
    (candidate) => `${candidate.interviewKey ?? ''} ${candidate.responsible ?? ''} ${candidate.violations.map((v) => v.reason).join(' ')}`
  );
  protected readonly running = signal(false);
  protected readonly confirmRun = signal(false);

  protected readonly variableOptions = computed<SearchSelectOption[]>(() =>
    this.store.questions().map((question) => ({
      value: question.variableName,
      label: `${question.label || question.questionText} (${question.variableName})`
    })));

  protected readonly numericOptions = computed<SearchSelectOption[]>(() =>
    this.store.questions()
      .filter((question) => /numeric/i.test(question.type))
      .map((question) => ({ value: question.variableName, label: `${question.label || question.questionText} (${question.variableName})` })));

  /** Types déjà présents : leur bouton d'ajout est désactivé (une carte par type). */
  protected readonly usedTypes = computed(() => new Set(this.cards().map((card) => card.type)));

  protected readonly dirty = computed(() => {
    const saved = this.saved();
    if (!saved) {
      return false;
    }
    return saved.mode !== this.mode()
      || saved.maxHqRejections !== this.maxHqRejections()
      || rulesSignature(rulesFromCards(cardsFromRules(saved.rules))) !== rulesSignature(rulesFromCards(this.cards()));
  });

  /** Nombre d'interviews concernées par question, d'après la dernière simulation. */
  protected readonly variableCounts = computed(() => {
    const counts: Record<string, Record<string, number>> = { REQUIRED_ANSWER: {}, VALUE_RANGE: {} };
    for (const count of this.preview()?.perRule ?? []) {
      if (count.variable && counts[count.type]) {
        counts[count.type][count.variable] = count.interviews;
      }
    }
    return counts;
  });

  protected readonly needsAutoConfirmation = computed(() =>
    this.mode() === 'AUTO' && this.saved()?.mode !== 'AUTO' && !this.autoConfirmed());

  protected readonly activeRules = computed(() => this.cards().filter((card) => card.enabled).length);

  constructor() {
    effect(() => {
      const questionnaire = this.store.questionnaire();
      if (questionnaire) {
        untracked(() => this.load());
      }
    });
  }

  protected load(): void {
    const questionnaire = this.store.questionnaire();
    if (!questionnaire) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.api.autoReject(questionnaire.questionnaireId, questionnaire.version).subscribe({
      next: (overview) => {
        this.apply(overview.settings);
        this.events.set(overview.recentEvents);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(apiErrorMessage(error, 'Les règles de rejet automatique ne sont pas disponibles.'));
        this.loading.set(false);
      }
    });
  }

  protected templateFor(type: AutoRejectRuleType): RuleTemplate {
    return RULE_TEMPLATES.find((template) => template.type === type)!;
  }

  protected modeHelp(): string {
    return MODES.find((mode) => mode.id === this.mode())?.help ?? '';
  }

  protected isUsed(type: AutoRejectRuleType): boolean {
    return this.usedTypes().has(type);
  }

  protected isGlobal(card: RuleCard): boolean {
    return GLOBAL_TYPES.has(card.type);
  }

  /** Questions proposées à une ligne de plage : celles des autres lignes sont exclues. */
  protected rangeOptions(card: RuleCard, index: number): SearchSelectOption[] {
    const taken = new Set(card.ranges.filter((_, i) => i !== index).map((range) => range.variable));
    return this.numericOptions().filter((option) => !taken.has(option.value));
  }

  protected addRule(type: AutoRejectRuleType): void {
    if (this.isUsed(type)) {
      return;
    }
    this.cards.update((cards) => [...cards, emptyCard(type, this.templateFor(type).defaults)]);
    this.changed();
  }

  protected updateCard(index: number, change: Partial<RuleCard>): void {
    this.cards.update((cards) => cards.map((card, i) => (i === index ? { ...card, ...change } : card)));
    this.changed();
  }

  protected removeCard(index: number): void {
    this.cards.update((cards) => cards.filter((_, i) => i !== index));
    this.changed();
  }

  protected addRange(index: number): void {
    const card = this.cards()[index];
    this.updateCard(index, { ranges: [...card.ranges, { variable: null, minValue: null, maxValue: null }] });
  }

  protected updateRange(index: number, rangeIndex: number, change: Partial<RangeItem>): void {
    const card = this.cards()[index];
    this.updateCard(index, { ranges: card.ranges.map((range, i) => (i === rangeIndex ? { ...range, ...change } : range)) });
  }

  protected removeRange(index: number, rangeIndex: number): void {
    const card = this.cards()[index];
    this.updateCard(index, { ranges: card.ranges.filter((_, i) => i !== rangeIndex) });
  }

  /** Toute modification invalide la simulation et les messages précédents. */
  private changed(): void {
    this.preview.set(null);
    this.error.set(null);
    this.notice.set(null);
  }

  protected setMode(mode: AutoRejectMode): void {
    this.mode.set(mode);
    this.autoConfirmed.set(false);
  }

  protected reset(): void {
    const saved = this.saved();
    if (saved) {
      this.apply(saved);
    }
  }

  protected save(): void {
    const questionnaire = this.store.questionnaire();
    if (!questionnaire || this.saving() || this.needsAutoConfirmation()) {
      return;
    }
    const problem = cardProblem(this.cards());
    if (problem) {
      this.notice.set(null);
      this.error.set(problem);
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.api.saveAutoReject(questionnaire.questionnaireId, questionnaire.version, {
      mode: this.mode(),
      maxHqRejections: this.maxHqRejections(),
      rules: rulesFromCards(this.cards())
    }).subscribe({
      next: (settings) => {
        this.apply(settings);
        this.saving.set(false);
        this.notice.set(settings.mode === 'AUTO'
          ? 'Règles enregistrées. Les rejets s’appliqueront à la prochaine synchronisation.'
          : 'Règles enregistrées.');
        if (settings.mode !== 'OFF') {
          this.runPreview();
        }
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(error, 'Les règles n’ont pas pu être enregistrées.'));
      }
    });
  }

  protected runPreview(): void {
    const questionnaire = this.store.questionnaire();
    if (!questionnaire) {
      return;
    }
    this.previewing.set(true);
    this.error.set(null);
    this.api.previewAutoReject(questionnaire.questionnaireId, questionnaire.version).subscribe({
      next: (preview) => {
        this.preview.set(preview);
        this.previewing.set(false);
      },
      error: (error: unknown) => {
        this.previewing.set(false);
        this.error.set(apiErrorMessage(error, 'La simulation n’a pas pu être calculée.'));
      }
    });
  }

  protected runNow(): void {
    const questionnaire = this.store.questionnaire();
    if (!questionnaire || this.running()) {
      return;
    }
    this.running.set(true);
    this.confirmRun.set(false);
    this.error.set(null);
    this.api.runAutoReject(questionnaire.questionnaireId, questionnaire.version).subscribe({
      next: (result) => {
        this.running.set(false);
        this.notice.set(`${result.rejected} interview(s) rejetée(s), ${result.skippedLimit} laissée(s) au superviseur `
          + `(plafond atteint), ${result.alreadyHandled} déjà traitée(s)${result.failed ? `, ${result.failed} échec(s)` : ''}.`);
        this.load();
        this.runPreview();
        this.store.refreshAfterInterviewAction();
      },
      error: (error: unknown) => {
        this.running.set(false);
        this.error.set(apiErrorMessage(error, 'Le rejet automatique n’a pas pu être exécuté.'));
      }
    });
  }

  /** Interviews concernées par une règle globale, d'après la dernière simulation. */
  protected cardCount(card: RuleCard): number | null {
    const preview = this.preview();
    if (!preview || !this.isGlobal(card)) {
      return null;
    }
    return preview.perRule.find((count) => count.type === card.type)?.interviews ?? 0;
  }

  protected rangeCount(range: RangeItem): number | null {
    return this.preview() && range.variable ? this.variableCounts()['VALUE_RANGE'][range.variable] ?? null : null;
  }

  protected outcomeLabel(outcome: AutoRejectEvent['outcome']): string {
    return { REJECTED: 'Rejetée', FAILED: 'Échec', SKIPPED_LIMIT: 'Plafond atteint' }[outcome];
  }

  protected date(value: string | null): string {
    return value
      ? new Date(value).toLocaleString('fr-FR', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })
      : '—';
  }

  protected statusLabel(status: string): string {
    return ({
      Completed: 'Terminée',
      ApprovedBySupervisor: 'Approuvée superviseur',
      ApprovedByHeadquarters: 'Approuvée par HQ',
      RejectedBySupervisor: 'Rejetée par le superviseur',
      RejectedByHeadquarters: 'Rejetée par HQ',
      InterviewerAssigned: 'Chez l’enquêteur'
    } as Record<string, string>)[status] ?? status;
  }

  /** Interviews en infraction qu'HQ peut encore rejeter (les seules concernées par le mode automatique). */
  protected rejectableCount(preview: AutoRejectPreview): number {
    return preview.candidates.filter((candidate) => candidate.rejectable).length;
  }

  private apply(settings: AutoRejectSettings): void {
    this.saved.set(settings);
    this.mode.set(settings.mode);
    this.maxHqRejections.set(settings.maxHqRejections);
    this.cards.set(cardsFromRules(settings.rules));
    this.autoConfirmed.set(false);
  }
}

