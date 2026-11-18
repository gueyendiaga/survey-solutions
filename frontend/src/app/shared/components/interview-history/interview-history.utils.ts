import { InterviewHistoryRecord } from '../../../core/models/dashboard.models';

/** Familles d'événements HQ, du plus utile au contrôleur au plus technique. */
export type HistoryCategory = 'workflow' | 'answer' | 'session' | 'system';
export type HistoryFilter = HistoryCategory | 'all';
export type HistoryTone = 'neutral' | 'info' | 'success' | 'danger' | 'warn';

export interface HistoryEntry {
  index: number;
  action: string;
  label: string;
  category: HistoryCategory;
  tone: HistoryTone;
  actor: string;
  role: string | null;
  at: Date | null;
  /** Détail lisible : paramètres de l'action, question et réponse… */
  detail: string | null;
  question: string | null;
  roster: string | null;
  previous: string | null;
  value: string | null;
  /** Réponse modifiée après une première saisie. */
  correction: boolean;
  /** Événements système consécutifs regroupés (vue « Tout »). */
  grouped: number;
  searchText: string;
}

export interface HistoryMilestone {
  label: string;
  at: Date | null;
  tone: HistoryTone;
}

export interface HistorySummary {
  activeSeconds: number | null;
  answers: number;
  corrections: number;
  removals: number;
  sessions: number;
  rejections: number;
  comments: number;
  invalid: number;
}

export interface HistoryModel {
  entries: HistoryEntry[];
  summary: HistorySummary;
  milestones: HistoryMilestone[];
  counts: Record<HistoryFilter, number>;
}

const LABELS: Record<string, string> = {
  InterviewCreated: 'Interview créée',
  SupervisorAssigned: 'Affectée à un superviseur',
  InterviewerAssigned: 'Affectée à un enquêteur',
  InterviewModeChanged: 'Mode de collecte défini',
  KeyAssigned: 'Clé HQ attribuée',
  Resumed: 'Saisie reprise',
  Paused: 'Saisie interrompue',
  AnswerSet: 'Réponse saisie',
  AnswerRemoved: 'Réponse effacée',
  CommentSet: 'Commentaire ajouté',
  AnswerCommented: 'Commentaire ajouté',
  Completed: 'Interview terminée',
  Restarted: 'Interview rouverte',
  ReceivedBySupervisor: 'Reçue par le superviseur',
  ReceivedByInterviewer: 'Reçue sur la tablette',
  ApproveBySupervisor: 'Approuvée par le superviseur',
  ApprovedBySupervisor: 'Approuvée par le superviseur',
  ApproveByHeadquarter: 'Approuvée par HQ',
  ApprovedByHeadquarter: 'Approuvée par HQ',
  RejectedBySupervisor: 'Rejetée par le superviseur',
  RejectBySupervisor: 'Rejetée par le superviseur',
  RejectedByHeadquarter: 'Rejetée par HQ',
  RejectByHeadquarter: 'Rejetée par HQ',
  UnapproveByHeadquarters: 'Approbation HQ retirée',
  UnapprovedByHeadquarter: 'Approbation HQ retirée',
  OpenedBySupervisor: 'Ouverte par le superviseur',
  ClosedBySupervisor: 'Fermée par le superviseur',
  TranslationSwitched: 'Langue changée',
  QuestionDeclaredValid: 'Question validée',
  QuestionDeclaredInvalid: 'Erreur de validation',
  VariableSet: 'Variable calculée',
  VariableDisabled: 'Variable désactivée',
  QuestionEnabled: 'Question activée',
  QuestionDisabled: 'Question désactivée',
  GroupEnabled: 'Section activée',
  GroupDisabled: 'Section désactivée',
  InterviewDeleted: 'Interview supprimée',
  InterviewRestored: 'Interview restaurée'
};

const ROLES: Record<string, string> = {
  Interviewer: 'Enquêteur',
  Supervisor: 'Superviseur',
  Headquarter: 'HQ',
  Headquarters: 'HQ',
  Administrator: 'Administrateur',
  ApiUser: 'Utilisateur API',
  Observer: 'Observateur'
};

const SESSION = new Set(['Paused', 'Resumed', 'OpenedBySupervisor', 'ClosedBySupervisor']);
const ANSWER = new Set(['AnswerSet', 'AnswerRemoved']);

export function historyCategory(action: string): HistoryCategory {
  if (ANSWER.has(action)) {
    return 'answer';
  }
  if (SESSION.has(action)) {
    return 'session';
  }
  if (/^(Variable|Question|Group|StaticText)/.test(action) || /(Enabled|Disabled|DeclaredValid|DeclaredInvalid)$/.test(action)) {
    return 'system';
  }
  return 'workflow';
}

export function historyTone(action: string): HistoryTone {
  if (/Reject|Invalid|Deleted/.test(action)) {
    return 'danger';
  }
  if (/Unapprove|Restarted|AnswerRemoved/.test(action)) {
    return 'warn';
  }
  if (/Approve|Completed/.test(action)) {
    return 'success';
  }
  if (/Comment|Assigned|Received|Created|ModeChanged/.test(action)) {
    return 'info';
  }
  return 'neutral';
}

export function historyLabel(action: string): string {
  return LABELS[action] ?? action.replace(/([a-z])([A-Z])/g, '$1 $2');
}

export function roleLabel(role: string | null): string | null {
  return role ? ROLES[role] ?? role : null;
}

/** « 1,2 » → « ligne 1 › 2 » ; vide pour le niveau principal. */
export function rosterLabel(roster: string | null | undefined): string | null {
  const parts = (roster ?? '').split(',').map((part) => part.trim()).filter(Boolean);
  return parts.length ? `ligne ${parts.join(' › ')}` : null;
}

function parameter(record: InterviewHistoryRecord, ...names: string[]): string | null {
  const parameters = record.parameters ?? {};
  for (const name of names) {
    const match = Object.keys(parameters).find((key) => key.toLowerCase() === name.toLowerCase());
    const value = match ? parameters[match] : null;
    if (value !== null && value !== undefined && value !== '') {
      return String(value);
    }
  }
  return null;
}

function workflowDetail(record: InterviewHistoryRecord): string | null {
  const parameters = Object.entries(record.parameters ?? {}).filter(([, value]) => value !== null && value !== '');
  if (!parameters.length) {
    return null;
  }
  const names: Record<string, string> = {
    assignment: 'Affectation n°',
    responsible: 'Responsable :',
    mode: 'Mode :',
    key: 'Clé :',
    comment: 'Commentaire :',
    devicetype: 'Appareil :',
    language: 'Langue :'
  };
  return parameters
    .map(([key, value]) => {
      const label = names[key.toLowerCase()];
      const text = key.toLowerCase() === 'devicetype' && value === 'Tablet' ? 'tablette' : value;
      return label ? (label.endsWith('°') ? `${label}${text}` : `${label} ${text}`) : `${key} : ${text}`;
    })
    .join(' · ');
}

function parseDate(value: string | null): Date | null {
  if (!value) {
    return null;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

/** Transforme le journal brut d'HQ en chronologie lisible, avec résumé et jalons. */
export function buildHistory(records: InterviewHistoryRecord[]): HistoryModel {
  const ordered = [...records].sort((a, b) => a.index - b.index);
  const lastAnswer = new Map<string, string | null>();
  const entries: HistoryEntry[] = [];
  const summary: HistorySummary = {
    activeSeconds: null, answers: 0, corrections: 0, removals: 0, sessions: 0, rejections: 0, comments: 0, invalid: 0
  };
  let sessionStart: Date | null = null;
  let active = 0;

  for (const record of ordered) {
    const category = historyCategory(record.action);
    const at = parseDate(record.timestamp);
    const question = category === 'answer' || category === 'system' ? parameter(record, 'question', 'variable') : null;
    const roster = parameter(record, 'roster');
    let previous: string | null = null;
    let value: string | null = null;
    let correction = false;
    let detail: string | null = null;

    if (category === 'answer' && question) {
      const key = `${question}|${roster ?? ''}`;
      const known = lastAnswer.has(key);
      previous = known ? lastAnswer.get(key) ?? null : null;
      value = record.action === 'AnswerRemoved' ? null : parameter(record, 'answer');
      correction = known && previous !== null;
      lastAnswer.set(key, value);
      if (record.action === 'AnswerSet') {
        summary.answers++;
        summary.corrections += correction ? 1 : 0;
      } else {
        summary.removals++;
      }
    } else if (category === 'system') {
      value = parameter(record, 'value');
      if (record.action === 'QuestionDeclaredInvalid') {
        summary.invalid++;
      }
    } else {
      detail = workflowDetail(record);
    }

    if (record.action === 'Resumed') {
      summary.sessions++;
      sessionStart = at;
    } else if ((record.action === 'Paused' || record.action === 'Completed') && sessionStart && at) {
      active += Math.max(0, (at.getTime() - sessionStart.getTime()) / 1000);
      sessionStart = null;
    }
    if (/Reject/.test(record.action)) {
      summary.rejections++;
    }
    if (/Comment/.test(record.action)) {
      summary.comments++;
    }

    const actor = record.originatorName?.trim() || 'Système';
    const role = roleLabel(record.originatorRole?.trim() || null);
    const label = historyLabel(record.action);
    entries.push({
      index: record.index,
      action: record.action,
      label,
      category,
      tone: historyTone(record.action),
      actor,
      role,
      at,
      detail,
      question,
      roster: rosterLabel(roster),
      previous,
      value,
      correction,
      grouped: 1,
      searchText: [label, actor, role, detail, question, value, previous].filter(Boolean).join(' ').toLocaleLowerCase('fr')
    });
  }

  summary.activeSeconds = summary.sessions ? Math.round(active) : null;
  const counts: Record<HistoryFilter, number> = { workflow: 0, answer: 0, session: 0, system: 0, all: entries.length };
  for (const entry of entries) {
    counts[entry.category]++;
  }
  return { entries, summary, milestones: milestones(entries), counts };
}

function milestones(entries: HistoryEntry[]): HistoryMilestone[] {
  const first = (pattern: RegExp) => entries.find((entry) => pattern.test(entry.action));
  const last = (pattern: RegExp) => [...entries].reverse().find((entry) => pattern.test(entry.action));
  const decision = last(/Approve|Reject|Unapprove/);
  const steps: HistoryMilestone[] = [
    { label: 'Créée', at: first(/^InterviewCreated$/)?.at ?? entries[0]?.at ?? null, tone: 'info' },
    { label: 'Première réponse', at: first(/^AnswerSet$/)?.at ?? null, tone: 'neutral' },
    { label: 'Terminée', at: last(/^Completed$/)?.at ?? null, tone: 'success' }
  ];
  if (decision) {
    steps.push({ label: decision.label, at: decision.at, tone: decision.tone });
  }
  return steps;
}

/** Entrées d'une vue : filtre, recherche et regroupement des rafales d'événements système. */
export function visibleEntries(
  entries: HistoryEntry[],
  filter: HistoryFilter,
  search: string,
  correctionsOnly: boolean
): HistoryEntry[] {
  const query = search.trim().toLocaleLowerCase('fr');
  const matching = entries.filter((entry) =>
    (filter === 'all' || entry.category === filter)
    && (!correctionsOnly || entry.correction || entry.action === 'AnswerRemoved')
    && (!query || entry.searchText.includes(query)));
  if (filter !== 'all' || query) {
    return matching;
  }
  const grouped: HistoryEntry[] = [];
  for (const entry of matching) {
    const previous = grouped[grouped.length - 1];
    if (entry.category === 'system' && previous?.category === 'system' && previous.grouped > 0) {
      grouped[grouped.length - 1] = {
        ...previous,
        label: 'Recalculs automatiques',
        detail: `${previous.grouped + 1} validations et variables calculées par le questionnaire`,
        question: null,
        value: null,
        tone: 'neutral',
        grouped: previous.grouped + 1
      };
    } else {
      grouped.push(entry);
    }
  }
  return grouped;
}

export function formatDuration(seconds: number | null): string {
  if (seconds === null) {
    return '—';
  }
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) {
    return `${minutes} min`;
  }
  return `${Math.floor(minutes / 60)} h ${String(minutes % 60).padStart(2, '0')}`;
}

/** Export CSV (séparateur « ; » pour Excel en français). */
export function historyCsv(entries: HistoryEntry[]): string {
  const cell = (value: string | number | null) => `"${String(value ?? '').replace(/"/g, '""')}"`;
  const header = ['N°', 'Date (UTC)', 'Action', 'Auteur', 'Rôle', 'Question', 'Ligne', 'Ancienne valeur', 'Valeur', 'Détail'];
  const rows = entries.map((entry) => [
    entry.index, entry.at?.toISOString() ?? '', entry.label, entry.actor, entry.role, entry.question,
    entry.roster, entry.previous, entry.value, entry.detail
  ].map(cell).join(';'));
  return '﻿' + [header.map(cell).join(';'), ...rows].join('\r\n');
}
