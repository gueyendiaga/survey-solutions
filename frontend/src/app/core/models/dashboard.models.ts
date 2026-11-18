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
  rosterTitleQuestionIds?: string[];
}

export interface AnswerCount {
  label: string;
  count: number;
  percentage: number;
}

export interface AnswerObservation {
  interviewNumber: number;
  interviewId: string;
  /** Clé HQ lisible (ex. 01-50-90-18). */
  interviewKey: string | null;
  status: string;
  /** Identifiant de ligne de roster ; vide au niveau principal. */
  rowKey: string;
  value: string;
}

export interface InterviewAnswer {
  variableName: string | null;
  answer: unknown;
  additionalFields: Record<string, unknown>;
}

export interface InterviewAnswers {
  answers: InterviewAnswer[];
}

export interface InterviewReviewItem {
  interviewId: string;
  interviewKey: string;
  status: string;
  interviewerName: string | null;
  hasErrors: boolean | null;
  errorsCount: number | null;
  unansweredCount: number | null;
  durationSeconds: number | null;
  createdAt: string | null;
  completedAt: string | null;
  lastActionAt: string | null;
}

export interface InterviewReviewPage {
  interviews: InterviewReviewItem[];
  totalCount: number;
  page: number;
  pageSize: number;
}

export interface InterviewHistoryRecord {
  index: number;
  action: string;
  originatorName: string | null;
  originatorRole: string | null;
  parameters: Record<string, string | null> | null;
  timestamp: string | null;
  offset: string | null;
}

export interface InterviewHistory {
  interviewId: string;
  questionnaireId: string;
  questionnaireVersion: number;
  records: InterviewHistoryRecord[];
}

export interface InterviewStatistics {
  answered: number | null;
  notAnswered: number | null;
  flagged: number | null;
  notFlagged: number | null;
  valid: number | null;
  invalid: number | null;
  withComments: number | null;
  forInterviewer: number | null;
  forSupervisor: number | null;
  interviewId: string;
  interviewKey: string | null;
  status: string | null;
  responsibleId: string | null;
  responsibleName: string | null;
  numberOfInterviewers: number | null;
  numberRejectionsBySupervisor: number | null;
  numberRejectionsByHq: number | null;
  interviewDuration: string | null;
  assignmentId: number | null;
  updatedAtUtc: string | null;
  webInterviewUrl: string | null;
}

export interface ReviewResponsible {
  userId: string;
  userName: string;
}

export type HeadquartersInterviewAction = 'hqapprove' | 'hqreject' | 'hqunapprove';

export interface InterviewActionResult {
  status: string;
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

/** Filtres globaux : null = non filtré. status : null = statuts par défaut, 'ALL' = tous. */
export interface AnalysisFilters {
  from: string | null;
  to: string | null;
  supervisor: string | null;
  interviewer: string | null;
  status: string | null;
  variable: string | null;
  value: string | null;
}

export const NO_FILTERS: AnalysisFilters = {
  from: null,
  to: null,
  supervisor: null,
  interviewer: null,
  status: null,
  variable: null,
  value: null
};

/** Rapport PDF archivé (génération automatique quotidienne). */
export interface ArchivedReport {
  id: number;
  generatedAt: string;
  trigger: 'SCHEDULED' | 'MANUAL';
  filename: string;
  sizeBytes: number;
}

export interface FilterOptions {
  supervisors: string[];
  interviewers: string[];
  statuses: string[];
}

export interface QuestionOption {
  answer: number;
  text: string;
}

export interface StatusTotal {
  status: string;
  count: number;
}

export interface DailyProgress {
  date: string;
  completed: number;
  cumulative: number;
}

export interface InterviewerProgress {
  interviewer: string;
  target: number;
  total: number;
  completed: number;
  approved: number;
  rejected: number;
  inProgress: number;
  withErrors: number;
  rejections: number;
  completionRate: number | null;
  medianDurationMinutes: number | null;
  activeDays: number;
  completedPerActiveDay: number | null;
  lastCompletion: string | null;
}

export interface TeamProgress {
  supervisor: string;
  target: number;
  completed: number;
  approved: number;
  rejected: number;
  inProgress: number;
  withErrors: number;
  completionRate: number | null;
  interviewers: InterviewerProgress[];
}

/** Réalisation par rapport aux affectations HQ. completed = interviews terminées au moins une fois. */
export interface FieldworkOverview {
  syncedAt: string | null;
  filtersApplied: boolean;
  target: number;
  unlimitedAssignments: number;
  completed: number;
  approved: number;
  rejected: number;
  inProgress: number;
  completionRate: number | null;
  statuses: StatusTotal[];
  daily: DailyProgress[];
  teams: TeamProgress[];
}

// ---- Opérations de terrain (tablettes, validation, zones, mode, rendez-vous) ----------------

/** ACTIVE, INACTIVE (aucune activité depuis inactivityDays), NEVER (jamais d'activité), LOCKED. */
export type TabletState = 'ACTIVE' | 'INACTIVE' | 'NEVER' | 'LOCKED';

export interface TabletSummary {
  interviewers: number;
  active: number;
  inactive: number;
  neverActive: number;
  pendingAssignments: number;
  withoutDevice: number;
  locked: number;
}

export interface InterviewerTablet {
  interviewer: string;
  supervisor: string | null;
  lastTabletContact: string | null;
  lastCompletion: string | null;
  daysSinceActivity: number | null;
  pendingAssignments: number;
  oldestPendingSince: string | null;
  locked: boolean;
  deviceLinked: boolean | null;
  state: TabletState;
}

export interface QueueStage {
  total: number;
  lessThanOneDay: number;
  oneToThreeDays: number;
  threeToSevenDays: number;
  moreThanSevenDays: number;
}

export interface WaitingInterview {
  interviewId: string;
  interviewKey: string;
  interviewer: string | null;
  supervisor: string | null;
  status: string;
  since: string | null;
  waitingDays: number | null;
}

export interface ValidationQueue {
  supervisor: QueueStage;
  headquarters: QueueStage;
  medianSupervisorReviewHours: number | null;
  medianHqReviewHours: number | null;
  oldest: WaitingInterview[];
}

export interface ZoneOption {
  variable: string;
  label: string;
  identifying: boolean;
}

export interface ZoneProgress {
  code: string | null;
  label: string;
  interviews: number;
  completed: number;
  approved: number;
  rejected: number;
  inProgress: number;
  approvalRate: number | null;
}

export interface ZoneBreakdown {
  variable: string | null;
  candidates: ZoneOption[];
  rows: ZoneProgress[];
}

export interface ModeCount {
  mode: 'CAPI' | 'CAWI' | 'UNKNOWN' | string;
  count: number;
}

export interface AppointmentItem {
  startAt: string;
  interviewKey: string | null;
  assignmentId: number | null;
  responsible: string | null;
  supervisor: string | null;
  overdue: boolean;
  comment: string | null;
}

export interface Appointments {
  upcoming: number;
  overdue: number;
  items: AppointmentItem[];
}

export interface FieldOperations {
  syncedAt: string | null;
  inactivityDays: number;
  tablets: TabletSummary;
  interviewers: InterviewerTablet[];
  validation: ValidationQueue;
  zones: ZoneBreakdown;
  modes: ModeCount[];
  appointments: Appointments;
}

/* ---- Rejet automatique ---------------------------------------------------------------- */
export type AutoRejectMode = 'OFF' | 'SIMULATION' | 'AUTO';
export type AutoRejectRuleType = 'VALIDATION_ERRORS' | 'MIN_DURATION' | 'MAX_UNANSWERED' | 'REQUIRED_ANSWER' | 'VALUE_RANGE';

export interface AutoRejectRule {
  id: number | null;
  type: AutoRejectRuleType;
  variable: string | null;
  minValue: number | null;
  maxValue: number | null;
  message: string | null;
  enabled: boolean;
}

export interface AutoRejectSettings {
  mode: AutoRejectMode;
  maxHqRejections: number;
  rules: AutoRejectRule[];
  updatedAt: string | null;
  updatedBy: string | null;
}

export interface AutoRejectEvent {
  interviewId: string;
  interviewKey: string | null;
  evaluatedAt: string;
  outcome: 'REJECTED' | 'FAILED' | 'SKIPPED_LIMIT';
  reasons: string;
  error: string | null;
}

export interface AutoRejectOverview {
  settings: AutoRejectSettings;
  recentEvents: AutoRejectEvent[];
}

export interface AutoRejectViolation {
  ruleId: number | null;
  type: AutoRejectRuleType;
  variable: string | null;
  reason: string;
}

export interface AutoRejectCandidate {
  interviewId: string;
  interviewKey: string | null;
  status: string;
  responsible: string | null;
  hqRejections: number;
  overLimit: boolean;
  /** Terminée ou approuvée par le superviseur : HQ peut encore la rejeter. */
  rejectable: boolean;
  violations: AutoRejectViolation[];
}

export interface AutoRejectPreview {
  /** Interviews synchronisées évaluées, tous statuts confondus. */
  evaluated: number;
  /** Parmi elles, celles qu'HQ peut encore rejeter. */
  pending: number;
  candidates: AutoRejectCandidate[];
  perRule: { ruleId: number | null; type: AutoRejectRuleType; variable: string | null; interviews: number }[];
}

export interface AutoRejectRunResult {
  evaluated: number;
  rejected: number;
  skippedLimit: number;
  alreadyHandled: number;
  failed: number;
}
