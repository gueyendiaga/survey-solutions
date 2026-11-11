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
  return { id: 'question-1', variableName: 'age', questionText: 'Quel âge ?', label: 'Âge', type: 'Numeric', breadcrumbs: [], optionCount: 0 };
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
