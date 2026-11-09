export interface Questionnaire {
  questionnaireIdentity: string;
  questionnaireId: string;
  version: number;
  title: string;
  variable: string;
  lastEntryDate: string | null;
  webModeEnabled: boolean;
}

export interface StatusCount {
  status: string;
  count: number;
}

export interface TrendPoint {
  date: string;
  count: number;
}

export interface Dashboard {
  generatedAt: string;
  questionnaires: Questionnaire[];
  questionnaireCount: number;
  interviewCount: number;
  responsibleCount: number;
  supervisorCount: number;
  selectedQuestionnaire: Questionnaire | null;
  statuses: StatusCount[];
  updatesByDay: TrendPoint[];
}

export interface QuestionSummary {
  id: string;
  variableName: string;
  questionText: string;
  label: string;
  type: string;
  breadcrumbs: string[];
  optionCount: number;
}

export interface AnswerCount {
  label: string;
  count: number;
  percentage: number;
}

export interface AnswerObservation {
  interviewNumber: number;
  /** Clé HQ lisible (ex. 01-50-90-18). */
  interviewKey: string | null;
  /** Identifiant de ligne de roster ; vide au niveau principal. */
  rowKey: string;
  value: string;
}

/** État de l'import des données d'une version dans la base Analytics. */
export interface SyncStatus {
  inProgress: boolean;
  lastSuccessAt: string | null;
  interviewCount: number | null;
  lastStatus: 'RUNNING' | 'SUCCEEDED' | 'FAILED' | null;
  lastError: string | null;
}

/** RESPONSES : la somme fait 100 %. RESPONDENTS (choix multiples) : % des réponses non vides. */
export type PercentageBase = 'RESPONSES' | 'RESPONDENTS' | 'NUMERIC_VALUES';

/** Comptes en unités d'observation : interviews, ou lignes de roster pour une variable de roster. */
export interface QuestionCompleteness {
  question: QuestionSummary;
  level: string | null;
  answeredCount: number;
  /** Question active mais sans réponse. */
  missingCount: number;
  /** Question désactivée par une condition. */
  notApplicableCount: number;
  answeredInterviewCount: number;
  /** answered / (answered + missing), hors non applicables. */
  responseRate: number;
}

export interface CompletenessOverview {
  interviewCount: number;
  variables: QuestionCompleteness[];
  includedStatuses: string[];
  excludedInterviewCount: number;
  syncedAt: string | null;
}

export interface QuestionAnalytics {
  question: QuestionSummary;
  interviewCount: number;
  answeredInterviewCount: number;
  responseCount: number;
  missingCount: number;
  notApplicableCount: number;
  responseRate: number;
  observations: AnswerObservation[];
  distribution: AnswerCount[];
  minimum: number | null;
  maximum: number | null;
  mean: number | null;
  median: number | null;
  sum: number | null;
  percentile05: number | null;
  percentile95: number | null;
  nonNumericResponseCount: number;
  percentageBase: PercentageBase;
  specialValues: AnswerCount[];
  includedStatuses: string[];
  excludedInterviewCount: number;
  syncedAt: string | null;
}