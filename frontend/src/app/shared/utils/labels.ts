import { QuestionSummary } from '../../core/models/dashboard.models';

/** Libellés français des statuts d'interview HQ. */
const STATUS_LABELS: Record<string, string> = {
  Restored: 'Restaurées',
  Created: 'Créées',
  SupervisorAssigned: 'Affectées au superviseur',
  InterviewerAssigned: 'Affectées à l’enquêteur',
  RejectedBySupervisor: 'Rejetées par le superviseur',
  ReadyForInterview: 'Prêtes pour entretien',
  SentToCapi: 'Envoyées vers CAPI',
  Restarted: 'Redémarrées',
  Completed: 'Terminées',
  ApprovedBySupervisor: 'Approuvées par le superviseur',
  RejectedByHeadquarters: 'Rejetées par HQ',
  ApprovedByHeadquarters: 'Approuvées par HQ',
  Deleted: 'Supprimées',
  Inconnu: 'Statut inconnu'
};

export function statusLabel(status: string): string {
  return STATUS_LABELS[status] ?? status;
}

export function statusList(statuses: string[] | null | undefined): string {
  return (statuses ?? []).map(statusLabel).join(', ');
}

/** Familles de questions proposées comme filtre de la liste des variables. */
export type VariableKind = 'choice' | 'numeric' | 'text' | 'other';

export const VARIABLE_KIND_LABELS: Record<VariableKind, string> = {
  choice: 'Choix',
  numeric: 'Numériques',
  text: 'Texte',
  other: 'Autres'
};

export function variableKind(question: QuestionSummary): VariableKind {
  const type = (question.type ?? '').toLowerCase();
  if (type.includes('single') || type.includes('multi') || type.includes('multy')) {
    return 'choice';
  }
  if (type.includes('numeric')) {
    return 'numeric';
  }
  return type.includes('text') ? 'text' : 'other';
}

/** Type de question en français, avec le nombre de modalités pour les choix. */
export function typeLabel(question: QuestionSummary): string {
  const type = (question.type ?? '').toLowerCase();
  const label = type.includes('multi') || type.includes('multy') ? 'Choix multiples'
    : type.includes('single') ? 'Choix unique'
    : type.includes('numeric') ? 'Numérique'
    : type.includes('date') ? 'Date'
    : type.includes('textlist') ? 'Liste de textes'
    : type.includes('text') ? 'Texte'
    : type.includes('gps') ? 'GPS'
    : type.includes('multimedia') ? 'Photo'
    : type.includes('audio') ? 'Audio'
    : type.includes('barcode') ? 'Code-barres'
    : question.type || 'Question';
  return question.optionCount ? `${label} · ${question.optionCount} modalités` : label;
}

/** Réponses libres ou géolocalisées : pas de graphique de distribution. */
export function isSensitiveQuestion(question: QuestionSummary): boolean {
  const type = (question.type ?? '').toLowerCase();
  return ['text', 'multimedia', 'barcode', 'area', 'audio', 'gps', 'geopoint', 'coordinate', 'geospatial']
    .some((kind) => type.includes(kind));
}
