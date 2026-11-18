import { AutoRejectRule, AutoRejectRuleType } from '../../../../core/models/dashboard.models';

/**
 * L'écran présente une carte par type de règle ; l'API stocke une règle par question.
 * Une carte « Réponse obligatoire » regroupe plusieurs questions, une carte « Valeur hors plage »
 * plusieurs lignes (question, minimum, maximum). Message et activation sont communs à la carte.
 */
export interface RangeItem {
  variable: string | null;
  minValue: number | null;
  maxValue: number | null;
}

export interface RuleCard {
  type: AutoRejectRuleType;
  enabled: boolean;
  message: string | null;
  /** Seuil des règles globales (erreurs, durée, non-réponses). */
  minValue: number | null;
  maxValue: number | null;
  /** Réponse obligatoire : questions contrôlées. */
  variables: string[];
  /** Valeur hors plage : une ligne par question. */
  ranges: RangeItem[];
}

export const GLOBAL_TYPES: ReadonlySet<AutoRejectRuleType> = new Set(['VALIDATION_ERRORS', 'MIN_DURATION', 'MAX_UNANSWERED']);

export function emptyCard(type: AutoRejectRuleType, defaults: Partial<RuleCard> = {}): RuleCard {
  return {
    type,
    enabled: true,
    message: null,
    minValue: null,
    maxValue: null,
    variables: [],
    ranges: type === 'VALUE_RANGE' ? [{ variable: null, minValue: null, maxValue: null }] : [],
    ...defaults
  };
}

/** Regroupe les règles de l'API en cartes, dans l'ordre de première apparition de chaque type. */
export function cardsFromRules(rules: AutoRejectRule[]): RuleCard[] {
  const cards = new Map<AutoRejectRuleType, RuleCard>();
  for (const rule of rules) {
    let card = cards.get(rule.type);
    if (!card) {
      card = { ...emptyCard(rule.type), ranges: [], enabled: rule.enabled, message: rule.message ?? null };
      cards.set(rule.type, card);
    }
    if (GLOBAL_TYPES.has(rule.type)) {
      card.minValue = rule.minValue;
      card.maxValue = rule.maxValue;
    } else if (rule.type === 'REQUIRED_ANSWER') {
      if (rule.variable && !card.variables.includes(rule.variable)) {
        card.variables.push(rule.variable);
      }
    } else {
      card.ranges.push({ variable: rule.variable, minValue: rule.minValue, maxValue: rule.maxValue });
    }
  }
  return [...cards.values()];
}

/**
 * Règles envoyées à l'API. Une carte sans question est transmise avec une variable vide :
 * le serveur la refuse alors avec un message explicite, plutôt que de la perdre en silence.
 */
export function rulesFromCards(cards: RuleCard[]): AutoRejectRule[] {
  const rule = (card: RuleCard, change: Partial<AutoRejectRule>): AutoRejectRule => ({
    id: null,
    type: card.type,
    variable: null,
    minValue: null,
    maxValue: null,
    message: card.message?.trim() ? card.message.trim() : null,
    enabled: card.enabled,
    ...change
  });
  return cards.flatMap((card) => {
    if (GLOBAL_TYPES.has(card.type)) {
      return [rule(card, { minValue: numberOrNull(card.minValue), maxValue: numberOrNull(card.maxValue) })];
    }
    if (card.type === 'REQUIRED_ANSWER') {
      return card.variables.length ? card.variables.map((variable) => rule(card, { variable })) : [rule(card, {})];
    }
    const ranges = card.ranges.length ? card.ranges : [{ variable: null, minValue: null, maxValue: null }];
    return ranges.map((range) => rule(card, {
      variable: range.variable,
      minValue: numberOrNull(range.minValue),
      maxValue: numberOrNull(range.maxValue)
    }));
  });
}

/** Contrôle avant envoi, avec un message qui désigne la carte à compléter. */
export function cardProblem(cards: RuleCard[]): string | null {
  for (const card of cards) {
    if (card.type === 'REQUIRED_ANSWER' && !card.variables.length) {
      return 'Choisissez au moins une question pour la règle « Réponse obligatoire ».';
    }
    if (card.type === 'VALUE_RANGE') {
      if (!card.ranges.length || card.ranges.some((range) => !range.variable)) {
        return 'Choisissez la question de chaque ligne de la règle « Valeur hors plage ».';
      }
      const missing = card.ranges.find((range) => numberOrNull(range.minValue) === null && numberOrNull(range.maxValue) === null);
      if (missing) {
        return `Indiquez un minimum ou un maximum pour « ${missing.variable} ».`;
      }
      const inverted = card.ranges.find((range) => {
        const min = numberOrNull(range.minValue);
        const max = numberOrNull(range.maxValue);
        return min !== null && max !== null && min > max;
      });
      if (inverted) {
        return `Le minimum dépasse le maximum pour « ${inverted.variable} ».`;
      }
    }
  }
  return null;
}

/** Forme comparable d'une liste de règles, pour détecter les modifications non enregistrées. */
export function rulesSignature(rules: AutoRejectRule[]): string {
  return JSON.stringify(rules.map((rule) => [
    rule.type, rule.variable ?? null, numberOrNull(rule.minValue), numberOrNull(rule.maxValue),
    rule.message?.trim() || null, rule.enabled
  ]));
}

export function numberOrNull(value: unknown): number | null {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}
