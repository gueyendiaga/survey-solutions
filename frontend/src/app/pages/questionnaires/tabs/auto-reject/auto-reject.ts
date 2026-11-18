import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AutoRejectEvent,
  AutoRejectMode,
  AutoRejectPreview,
  AutoRejectRule,
  AutoRejectRuleType,
  AutoRejectSettings
} from '../../../../core/models/dashboard.models';
import { DashboardApiService } from '../../../../core/services/dashboard-api.service';
import { SearchSelectComponent, SearchSelectOption } from '../../../../shared/components/search-select/search-select';
import { apiErrorMessage } from '../../../../shared/utils/http-utils';
import { QuestionnaireStore } from '../../questionnaire.store';

interface RuleTemplate {
  type: AutoRejectRuleType;
  label: string;
  description: string;
  defaults: Partial<AutoRejectRule>;
}

export const RULE_TEMPLATES: RuleTemplate[] = [
  { type: 'VALIDATION_ERRORS', label: 'Erreurs de validation', description: 'Au moins N erreurs de validation non résolues.', defaults: { minValue: 1 } },
  { type: 'MIN_DURATION', label: 'Durée trop courte', description: 'Interview saisie en moins de N minutes.', defaults: { minValue: 15 } },
  { type: 'MAX_UNANSWERED', label: 'Trop de non-réponses', description: 'Plus de N questions actives laissées sans réponse.', defaults: { maxValue: 5 } },
  { type: 'REQUIRED_ANSWER', label: 'Réponse obligatoire', description: 'Une question active sans réponse.', defaults: {} },
  { type: 'VALUE_RANGE', label: 'Valeur hors plage', description: 'Valeur numérique hors des bornes attendues.', defaults: {} }
];

/** Règles globales : une seule par questionnaire. Les autres portent sur une question. */
const GLOBAL_RULES = new Set<AutoRejectRuleType>(['VALIDATION_ERRORS', 'MIN_DURATION', 'MAX_UNANSWERED']);

const MODES: { id: AutoRejectMode; label: string; help: string }[] = [
  { id: 'OFF', label: 'Désactivé', help: 'Les règles sont conservées mais ne sont pas évaluées.' },
  { id: 'SIMULATION', label: 'Simulation', help: 'Les règles signalent les interviews concernées ; aucune n’est rejetée.' },
  { id: 'AUTO', label: 'Automatique', help: 'Après chaque synchronisation, HQ rejette les interviews en infraction avec un commentaire.' }
];

/** Paramétrage des rejets automatiques d'une version de questionnaire, avec simulation préalable. */
@Component({
  selector: 'app-auto-reject',
  standalone: true,
  imports: [FormsModule, SearchSelectComponent],
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
  protected readonly rules = signal<AutoRejectRule[]>([]);
  protected readonly autoConfirmed = signal(false);
  protected readonly preview = signal<AutoRejectPreview | null>(null);
  protected readonly previewing = signal(false);
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

  /** Règles globales déjà présentes : leur bouton d'ajout est désactivé. */
  protected readonly usedGlobalTypes = computed(() =>
    new Set(this.rules().map((rule) => rule.type).filter((type) => GLOBAL_RULES.has(type))));

  protected readonly dirty = computed(() => {
    const saved = this.saved();
    if (!saved) {
      return false;
    }
    return saved.mode !== this.mode()
      || saved.maxHqRejections !== this.maxHqRejections()
      || JSON.stringify(saved.rules.map(comparable)) !== JSON.stringify(this.rules().map(comparable));
  });

  protected readonly needsAutoConfirmation = computed(() =>
    this.mode() === 'AUTO' && this.saved()?.mode !== 'AUTO' && !this.autoConfirmed());

  protected readonly activeRules = computed(() => this.rules().filter((rule) => rule.enabled).length);

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

  protected isGlobalUsed(type: AutoRejectRuleType): boolean {
    return this.usedGlobalTypes().has(type);
  }

  /** Questions proposées à une règle : celles déjà prises par une autre règle du même type sont exclues. */
  protected optionsFor(index: number): SearchSelectOption[] {
    const rule = this.rules()[index];
    const base = rule.type === 'VALUE_RANGE' ? this.numericOptions() : this.variableOptions();
    const taken = new Set(this.rules()
      .filter((other, i) => i !== index && other.type === rule.type && other.variable)
      .map((other) => other.variable));
    return base.filter((option) => !taken.has(option.value));
  }

  protected addRule(type: AutoRejectRuleType): void {
    if (this.isGlobalUsed(type)) {
      return;
    }
    const template = this.templateFor(type);
    this.rules.update((rules) => [...rules, {
      id: null, type, variable: null, minValue: null, maxValue: null, message: null, enabled: true, ...template.defaults
    }]);
    this.preview.set(null);
  }

  protected updateRule(index: number, change: Partial<AutoRejectRule>): void {
    this.rules.update((rules) => rules.map((rule, i) => (i === index ? { ...rule, ...change } : rule)));
    this.preview.set(null);
  }

  protected removeRule(index: number): void {
    this.rules.update((rules) => rules.filter((_, i) => i !== index));
    this.preview.set(null);
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
    this.saving.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.api.saveAutoReject(questionnaire.questionnaireId, questionnaire.version, {
      mode: this.mode(),
      maxHqRejections: this.maxHqRejections(),
      rules: this.rules().map((rule) => ({ ...rule, minValue: numberOrNull(rule.minValue), maxValue: numberOrNull(rule.maxValue) }))
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

  protected ruleCount(index: number): number | null {
    const rule = this.rules()[index];
    const preview = this.preview();
    if (!preview || rule.id === null) {
      return null;
    }
    return preview.perRule.find((count) => count.ruleId === rule.id)?.interviews ?? 0;
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
    return status === 'ApprovedBySupervisor' ? 'Approuvée superviseur' : 'Terminée';
  }

  private apply(settings: AutoRejectSettings): void {
    this.saved.set(settings);
    this.mode.set(settings.mode);
    this.maxHqRejections.set(settings.maxHqRejections);
    this.rules.set(settings.rules.map((rule) => ({ ...rule })));
    this.autoConfirmed.set(false);
  }
}

function numberOrNull(value: unknown): number | null {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function comparable(rule: AutoRejectRule): unknown[] {
  return [rule.type, rule.variable ?? null, numberOrNull(rule.minValue), numberOrNull(rule.maxValue), rule.message || null, rule.enabled];
}
