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
  value: string;
}

export interface QuestionCompleteness {
  question: QuestionSummary;
  answeredInterviewCount: number;
  interviewWithoutAnswerCount: number;
  responseRate: number;
}

export interface CompletenessOverview {
  interviewCount: number;
  variables: QuestionCompleteness[];
}

export interface QuestionAnalytics {
  question: QuestionSummary;
  interviewCount: number;
  answeredInterviewCount: number;
  interviewWithoutAnswerCount: number;
  responseCount: number;
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
}
