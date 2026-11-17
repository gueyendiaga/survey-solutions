/** Réponses d'API factices partagées par les tests (forme JSON réelle du backend). */
export const QUESTIONNAIRE_ID = '588daf2d-0c72-4b89-bec9-254459a68983';
export const BASE = `/api/questionnaires/${QUESTIONNAIRE_ID}/1`;

export const QUESTIONNAIRE = {
  questionnaireIdentity: '588daf2d0c724b89bec9254459a68983$1',
  questionnaireId: QUESTIONNAIRE_ID,
  version: 1,
  title: 'Test questionnaire',
  variable: 'survey_variable',
  lastEntryDate: '2026-11-03T10:38:49.13373',
  webModeEnabled: false
};

export function dashboardResponse() {
  return {
    GeneratedAt: '2026-11-03T20:00:00Z',
    Questionnaires: [{
      QuestionnaireIdentity: QUESTIONNAIRE.questionnaireIdentity,
      QuestionnaireId: QUESTIONNAIRE_ID,
      Version: 1,
      Title: 'Test questionnaire',
      Variable: 'survey_variable',
      LastEntryDate: '2026-11-03T10:38:49.13373',
      AudioRecordingEnabled: false,
      WebModeEnabled: false
    }],
    QuestionnaireCount: 1,
    InterviewCount: 1,
    ResponsibleCount: 1,
    SupervisorCount: 1,
    SelectedQuestionnaire: null,
    Statuses: [{ Status: 'Completed', Count: 1 }],
    UpdatesByDay: [{ Date: '2026-11-03', Count: 1 }]
  };
}

export function syncResponse() {
  return { inProgress: false, lastSuccessAt: '2026-11-09T10:00:00Z', interviewCount: 2, lastStatus: 'SUCCEEDED', lastError: null };
}

export function ageQuestion() {
  return {
    id: 'question-1',
    variableName: 'age',
    questionText: 'Quel âge ?',
    label: 'Âge',
    type: 'Numeric',
    breadcrumbs: [],
    optionCount: 0,
    rosterTitleQuestionIds: [] as string[]
  };
}

export function completenessResponse() {
  return {
    interviewCount: 2,
    variables: [{
      question: ageQuestion(), level: 'survey', answeredCount: 1, missingCount: 1, notApplicableCount: 3,
      answeredInterviewCount: 1, responseRate: 50
    }],
    includedStatuses: ['Completed'],
    excludedInterviewCount: 0,
    syncedAt: '2026-11-09T10:00:00Z'
  };
}

export function analyticsResponse() {
  return {
    question: ageQuestion(),
    interviewCount: 1,
    answeredInterviewCount: 1,
    responseCount: 1,
    missingCount: 0,
    notApplicableCount: 2,
    responseRate: 100,
    observations: [{ interviewNumber: 1, interviewId: 'interview-1', interviewKey: '01-50-90-18', rowKey: '', value: '20' }],
    distribution: [{ label: '20', count: 1, percentage: 100 }],
    minimum: 20, maximum: 20, mean: 20, median: 20, sum: 20, percentile05: 20, percentile95: 20,
    nonNumericResponseCount: 0,
    percentageBase: 'NUMERIC_VALUES',
    specialValues: [{ label: 'Ne sait pas', count: 1, percentage: 50 }],
    includedStatuses: ['Completed', 'ApprovedByHeadquarters'],
    excludedInterviewCount: 3,
    syncedAt: '2026-11-09T10:00:00Z'
  };
}

export function fieldworkResponse() {
  return {
    syncedAt: '2026-11-09T10:00:00Z',
    filtersApplied: false,
    target: 6,
    unlimitedAssignments: 0,
    completed: 4,
    approved: 4,
    rejected: 0,
    inProgress: 0,
    completionRate: 66.67,
    statuses: [{ status: 'ApprovedByHeadquarters', count: 4 }],
    daily: [{ date: '2026-11-03', completed: 1, cumulative: 1 }, { date: '2026-11-05', completed: 3, cumulative: 4 }],
    teams: [{
      supervisor: 'sup1', target: 6, completed: 4, approved: 4, rejected: 0, inProgress: 0, withErrors: 0, completionRate: 66.67,
      interviewers: [
        { interviewer: 'enq1', target: 5, total: 4, completed: 4, approved: 4, rejected: 0, inProgress: 0, withErrors: 0,
          rejections: 0, completionRate: 80, medianDurationMinutes: 12.5, activeDays: 2, completedPerActiveDay: 2,
          lastCompletion: '2026-11-05' },
        { interviewer: 'enq2', target: 1, total: 0, completed: 0, approved: 0, rejected: 0, inProgress: 0, withErrors: 0,
          rejections: 0, completionRate: 0, medianDurationMinutes: null, activeDays: 0, completedPerActiveDay: null,
          lastCompletion: null }
      ]
    }]
  };
}

export function filterOptionsResponse() {
  return { supervisors: ['sup1'], interviewers: ['enq1', 'enq2'], statuses: ['Completed'] };
}

export function operationsResponse() {
  return {
    syncedAt: '2026-11-17T10:00:00Z',
    inactivityDays: 3,
    tablets: { interviewers: 2, active: 1, inactive: 0, neverActive: 1, pendingAssignments: 2, withoutDevice: 1, locked: 0 },
    interviewers: [
      { interviewer: 'enq1', supervisor: 'sup1', lastTabletContact: '2026-11-16T08:00:00Z', lastCompletion: '2026-11-16T18:00:00Z',
        daysSinceActivity: 0, pendingAssignments: 0, oldestPendingSince: null, locked: false, deviceLinked: true, state: 'ACTIVE' },
      { interviewer: 'enq2', supervisor: 'sup1', lastTabletContact: null, lastCompletion: null,
        daysSinceActivity: null, pendingAssignments: 2, oldestPendingSince: '2026-11-10T08:00:00Z', locked: false, deviceLinked: false, state: 'NEVER' }
    ],
    validation: {
      supervisor: { total: 3, lessThanOneDay: 1, oneToThreeDays: 0, threeToSevenDays: 1, moreThanSevenDays: 1 },
      headquarters: { total: 1, lessThanOneDay: 1, oneToThreeDays: 0, threeToSevenDays: 0, moreThanSevenDays: 0 },
      medianSupervisorReviewHours: 30,
      medianHqReviewHours: 5.5,
      oldest: [{ interviewId: 'i-1', interviewKey: '01-50-90-18', interviewer: 'enq1', supervisor: 'sup1',
        status: 'Completed', since: '2026-11-08T08:00:00Z', waitingDays: 9.2 }]
    },
    zones: {
      variable: 'region',
      candidates: [{ variable: 'region', label: 'Région', identifying: true }, { variable: 'milieu', label: 'Milieu', identifying: false }],
      rows: [{ code: '1', label: 'Dakar', interviews: 3, completed: 3, approved: 2, rejected: 1, inProgress: 0, approvalRate: 66.7 }]
    },
    modes: [{ mode: 'CAPI', count: 3 }, { mode: 'CAWI', count: 1 }],
    appointments: { upcoming: 1, overdue: 1, items: [
      { startAt: '2026-11-15T09:00:00Z', interviewKey: '02-11-22-33', assignmentId: null, responsible: 'enq1', supervisor: 'sup1', overdue: true, comment: 'Rappeler' }
    ] }
  };
}
