import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { By } from '@angular/platform-browser';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { App, shortLabel } from './app';
import { DashboardChartComponent } from './dashboard-chart';
import { FieldworkPanelComponent } from './fieldwork-panel';

const BASE = '/api/questionnaires/588daf2d-0c72-4b89-bec9-254459a68983/1';

describe('shortLabel', () => {
  it('wraps long option labels on two lines and truncates the rest', () => {
    const label = 'YES, I consent to the sharing of my anonymized survey responses with third parties';
    const short = shortLabel(label, 30, 2);
    const lines = short.split('\n');

    expect(lines.length).toBe(2);
    expect(lines.every((line) => line.length <= 30)).toBeTrue();
    expect(short.endsWith('…')).toBeTrue();
    expect(shortLabel('Oui', 30, 2)).toBe('Oui');
  });
});

describe('FieldworkPanelComponent.continuousDays', () => {
  it('fills days without completion so the time axis stays regular', () => {
    const days = FieldworkPanelComponent.continuousDays([
      { date: '2026-11-03', completed: 1, cumulative: 1 },
      { date: '2026-11-05', completed: 3, cumulative: 4 }
    ]);

    expect(days.map((day) => day.date)).toEqual(['2026-11-03', '2026-11-04', '2026-11-05']);
    expect(days.map((day) => day.completed)).toEqual([1, 0, 3]);
    expect(days.map((day) => day.cumulative)).toEqual([1, 1, 4]);
  });
});

describe('App', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function element(fixture: ComponentFixture<App>): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function startOnDashboard(): ComponentFixture<App> {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    http.expectOne('/api/dashboard').flush(dashboardResponse());
    fixture.detectChanges();
    return fixture;
  }

  /** Ouvre le questionnaire depuis le catalogue : l'écran d'arrivée est le suivi terrain. */
  function openQuestionnaire(fixture: ComponentFixture<App>): void {
    (element(fixture).querySelector('a[href="#questionnaires"]') as HTMLAnchorElement).click();
    fixture.detectChanges();
    (element(fixture).querySelector('.open-questionnaire-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    http.expectOne(`${BASE}/sync`).flush(syncResponse());
    http.expectOne(`${BASE}/filter-options`)
      .flush({ supervisors: ['sup1'], interviewers: ['enq1', 'enq2'], statuses: ['Completed'] });
    http.expectOne(`${BASE}/fieldwork`).flush(fieldworkResponse());
    http.expectOne(`${BASE}/question-list`).flush([ageQuestion()]);
    fixture.detectChanges();
    http.expectOne(`${BASE}/question-completeness`).flush(completenessResponse());
    http.expectOne((request) => request.url === `${BASE}/question-analytics`
      && request.params.get('variableName') === 'age').flush(analyticsResponse());
    fixture.detectChanges();
  }

  it('renders dashboard figures and the questionnaire catalog', () => {
    const fixture = startOnDashboard();
    const content = element(fixture);

    expect(content.querySelector('h1')?.textContent).toContain('Tableau de bord');
    expect(content.querySelector('.kpis')?.textContent).toContain('Questionnaires');
    expect(content.querySelector('tbody')?.textContent).toContain('Test questionnaire');
    expect(content.querySelector('tbody')?.textContent).toContain('survey_variable');
    expect(content.querySelector('a[href="#fieldwork"]')).toBeNull();
  });

  it('opens a questionnaire on fieldwork with its own navigation and attention points', () => {
    const fixture = startOnDashboard();
    openQuestionnaire(fixture);
    const content = element(fixture);

    expect(content.querySelector('h1')?.textContent).toContain('Suivi terrain');
    expect(content.querySelector('.crumbs')?.textContent).toContain('Test questionnaire (v1)');
    expect(content.querySelector('.context-card')?.textContent).toContain('Version 1');
    expect(content.querySelector('.nav-item.active')?.textContent).toContain('Suivi terrain');
    expect(content.querySelector('.sync-line')?.textContent).toContain('2 interview(s)');
    const panel = content.querySelector('app-fieldwork-panel') as HTMLElement;
    expect(panel.querySelector('.kpis')?.textContent).toContain('66,7 %');
    expect(panel.querySelector('.team-row')?.textContent).toContain('sup1');
    expect(panel.querySelector('.interviewer-row.not-started')?.textContent).toContain('Pas commencé');
    expect(panel.querySelector('.attention')?.textContent).toContain('enq2 n’a pas commencé');

    (content.querySelector('a[href="#fieldwork"]') as HTMLAnchorElement).click();
    fixture.detectChanges();
  });

  it('sends the applied filters to fieldwork, completeness and variable analytics', fakeAsync(() => {
    const fixture = startOnDashboard();
    openQuestionnaire(fixture);
    tick();
    fixture.detectChanges();
    (element(fixture).querySelector('.filter-chips .chip') as HTMLButtonElement).click();
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    const interviewer = element(fixture).querySelector('select[name="interviewer"]') as HTMLSelectElement;
    interviewer.value = interviewer.options[1].value;
    interviewer.dispatchEvent(new Event('change'));
    const from = element(fixture).querySelector('input[name="from"]') as HTMLInputElement;
    from.value = '2026-11-01';
    from.dispatchEvent(new Event('input'));
    tick();
    fixture.detectChanges();
    (element(fixture).querySelector('.filter-apply') as HTMLButtonElement).click();
    fixture.detectChanges();

    const fieldwork = http.expectOne((request) => request.url === `${BASE}/fieldwork`);
    expect(fieldwork.request.params.get('interviewer')).toBe('enq1');
    expect(fieldwork.request.params.get('from')).toBe('2026-11-01');
    expect(fieldwork.request.params.has('statuses')).toBeFalse();
    fieldwork.flush({ ...fieldworkResponse(), filtersApplied: true });
    http.expectOne((request) => request.url === `${BASE}/question-completeness`
      && request.params.get('interviewer') === 'enq1').flush(completenessResponse());
    http.expectOne((request) => request.url === `${BASE}/question-analytics`
      && request.params.get('from') === '2026-11-01').flush(analyticsResponse());
    fixture.detectChanges();

    expect(element(fixture).querySelector('.fieldwork-note')?.textContent).toContain('Filtres actifs');
    expect(element(fixture).querySelector('.filter-bar')).toBeNull();
    expect(element(fixture).querySelector('.filter-chips')?.textContent).toContain('Enquêteur : enq1');
  }));

  it('shows variable indicators, distribution and individual answers', () => {
    const fixture = startOnDashboard();
    openQuestionnaire(fixture);
    (element(fixture).querySelector('a[href="#variables"]') as HTMLAnchorElement).click();
    fixture.detectChanges();
    const content = element(fixture);

    expect(content.querySelector('h1')?.textContent).toContain('Variables');
    expect(content.querySelector('.question-list-item.selected')?.textContent).toContain('age');
    expect(content.querySelector('.question-head .quality-status')?.textContent).toContain('À examiner');
    expect(content.querySelector('.question-kpi-grid')?.textContent).toContain('100 %');
    expect(content.querySelector('.distribution-table')?.textContent).toContain('20');
    expect(content.querySelector('.special-values-panel')?.textContent).toContain('Ne sait pas');
    expect(content.querySelector('.interview-key')?.textContent).toContain('01-50-90-18');

    const pie = content.querySelector('[aria-label="Afficher un graphique circulaire"]') as HTMLButtonElement;
    expect(pie.getAttribute('aria-pressed')).toBe('false');
    pie.click();
    fixture.detectChanges();
    expect(pie.getAttribute('aria-pressed')).toBe('true');

    const chart = fixture.debugElement.queryAll(By.directive(DashboardChartComponent)).at(-1)!
      .componentInstance as DashboardChartComponent;
    spyOn(chart, 'download');
    (content.querySelector('[aria-label="Télécharger le graphique en PNG"]') as HTMLButtonElement).click();
    expect(chart.download).toHaveBeenCalled();
  });

  it('lists variable completeness and opens a variable from the quality screen', () => {
    const fixture = startOnDashboard();
    openQuestionnaire(fixture);
    (element(fixture).querySelector('a[href="#quality"]') as HTMLAnchorElement).click();
    fixture.detectChanges();
    const content = element(fixture);

    expect(content.querySelector('h1')?.textContent).toContain('Qualité des données');
    expect(content.querySelector('.kpis')?.textContent).toContain('50 %');
    const row = content.querySelector('tbody tr') as HTMLElement;
    expect(row.textContent).toContain('age');
    expect(row.querySelector('.quality-status')?.textContent).toContain('À examiner');

    (row.querySelector('button.link') as HTMLButtonElement).click();
    fixture.detectChanges();
    http.expectOne((request) => request.url === `${BASE}/question-analytics`).flush(analyticsResponse());
    fixture.detectChanges();
    expect(content.querySelector('h1')?.textContent).toContain('Variables');
  });

  it('downloads the PDF report, archived reports and CSV exports', () => {
    spyOn(URL, 'createObjectURL').and.returnValue('blob:export');
    spyOn(URL, 'revokeObjectURL');
    spyOn(HTMLAnchorElement.prototype, 'click').and.callThrough();
    const fixture = startOnDashboard();
    openQuestionnaire(fixture);
    (element(fixture).querySelector('a[href="#reports"]') as HTMLAnchorElement).click();
    fixture.detectChanges();
    http.expectOne(`${BASE}/reports`).flush([
      { id: 7, generatedAt: '2026-11-09T07:00:00Z', trigger: 'SCHEDULED', filename: 'rapport.pdf', sizeBytes: 51200 }
    ]);
    fixture.detectChanges();
    const content = element(fixture);
    expect(content.textContent).toContain('rapport.pdf');

    (content.querySelector('.report-button') as HTMLButtonElement).click();
    http.expectOne((request) => request.url.endsWith('/report.pdf'))
      .flush(new Blob(['%PDF-'], { type: 'application/pdf' }));
    fixture.detectChanges();

    const menu = content.querySelector('.menu > .btn') as HTMLButtonElement;
    menu.click();
    fixture.detectChanges();
    const csv = [...content.querySelectorAll('.export-button')]
      .find((button) => button.textContent?.includes('CSV')) as HTMLButtonElement;
    csv.click();
    const exportRequest = http.expectOne((request) => request.url === `${BASE}/export`
      && request.params.get('format') === 'csv');
    expect(exportRequest.request.responseType).toBe('blob');
    exportRequest.flush(new Blob(['csv data'], { type: 'text/csv' }));
    fixture.detectChanges();

    expect(URL.createObjectURL).toHaveBeenCalledTimes(2);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:export');
    expect(content.querySelector('.menu-list')).toBeNull();
  });

  it('waits for the data import and reloads indicators when it finishes', fakeAsync(() => {
    const pending = { status: 409, statusText: 'Conflict' };
    const fixture = startOnDashboard();
    (element(fixture).querySelector('a[href="#questionnaires"]') as HTMLAnchorElement).click();
    fixture.detectChanges();
    (element(fixture).querySelector('.open-questionnaire-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    http.expectOne(`${BASE}/sync`).flush({
      inProgress: false, lastSuccessAt: null, interviewCount: null, lastStatus: null, lastError: null
    });
    http.expectOne(`${BASE}/filter-options`).flush({ supervisors: [], interviewers: [], statuses: [] });
    http.expectOne(`${BASE}/fieldwork`).flush({ detail: 'Synchronisation en cours', syncPending: true }, pending);
    http.expectOne(`${BASE}/question-list`).flush([ageQuestion()]);
    fixture.detectChanges();
    http.expectOne(`${BASE}/question-completeness`).flush({ syncPending: true }, pending);
    http.expectOne((request) => request.url === `${BASE}/question-analytics`).flush({ syncPending: true }, pending);
    fixture.detectChanges();

    expect(element(fixture).querySelector('.sync-line')?.textContent).toContain('Synchronisation en cours');
    expect(element(fixture).querySelector('.alert')).toBeNull();
    expect(element(fixture).textContent).toContain('Import des données en cours');

    tick(3000);
    http.expectOne(`${BASE}/sync`).flush(syncResponse());
    fixture.detectChanges();
    http.expectOne(`${BASE}/filter-options`).flush({ supervisors: [], interviewers: [], statuses: [] });
    http.expectOne(`${BASE}/fieldwork`).flush(fieldworkResponse());
    http.expectOne(`${BASE}/question-completeness`).flush(completenessResponse());
    http.expectOne((request) => request.url === `${BASE}/question-analytics`).flush(analyticsResponse());
    fixture.detectChanges();
    expect(element(fixture).querySelector('.sync-line')?.textContent).toContain('2 interview(s)');
  }));

  function syncResponse() {
    return {
      inProgress: false,
      lastSuccessAt: '2026-11-09T10:00:00Z',
      interviewCount: 2,
      lastStatus: 'SUCCEEDED',
      lastError: null
    };
  }

  function ageQuestion() {
    return {
      id: 'question-1',
      variableName: 'age',
      questionText: 'Quel âge ?',
      label: 'Âge',
      type: 'Numeric',
      breadcrumbs: [],
      optionCount: 0
    };
  }

  function completenessResponse() {
    return {
      interviewCount: 2,
      variables: [{
        question: ageQuestion(),
        level: 'survey',
        answeredCount: 1,
        missingCount: 1,
        notApplicableCount: 3,
        answeredInterviewCount: 1,
        responseRate: 50
      }],
      includedStatuses: ['Completed'],
      excludedInterviewCount: 0,
      syncedAt: '2026-11-09T10:00:00Z'
    };
  }

  function analyticsResponse() {
    return {
      question: ageQuestion(),
      interviewCount: 1,
      answeredInterviewCount: 1,
      responseCount: 1,
      missingCount: 0,
      notApplicableCount: 2,
      responseRate: 100,
      observations: [{ interviewNumber: 1, interviewKey: '01-50-90-18', rowKey: '', value: '20' }],
      distribution: [{ label: '20', count: 1, percentage: 100 }],
      minimum: 20,
      maximum: 20,
      mean: 20,
      median: 20,
      sum: 20,
      percentile05: 20,
      percentile95: 20,
      nonNumericResponseCount: 0,
      percentageBase: 'NUMERIC_VALUES',
      specialValues: [{ label: 'Ne sait pas', count: 1, percentage: 50 }],
      includedStatuses: ['Completed', 'ApprovedByHeadquarters'],
      excludedInterviewCount: 3,
      syncedAt: '2026-11-09T10:00:00Z'
    };
  }

  function fieldworkResponse() {
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
        supervisor: 'sup1', target: 6, completed: 4, approved: 4, rejected: 0, inProgress: 0, withErrors: 0,
        completionRate: 66.67,
        interviewers: [
          { interviewer: 'enq1', target: 5, total: 4, completed: 4, approved: 4, rejected: 0, inProgress: 0,
            withErrors: 0, rejections: 0, completionRate: 80, medianDurationMinutes: 12.5, activeDays: 2,
            completedPerActiveDay: 2, lastCompletion: '2026-11-05' },
          { interviewer: 'enq2', target: 1, total: 0, completed: 0, approved: 0, rejected: 0, inProgress: 0,
            withErrors: 0, rejections: 0, completionRate: 0, medianDurationMinutes: null, activeDays: 0,
            completedPerActiveDay: null, lastCompletion: null }
        ]
      }]
    };
  }

  function dashboardResponse() {
    return {
      GeneratedAt: '2026-11-03T20:00:00Z',
      Questionnaires: [
        {
          QuestionnaireIdentity: '588daf2d0c724b89bec9254459a68983$1',
          QuestionnaireId: '588daf2d-0c72-4b89-bec9-254459a68983',
          Version: 1,
          Title: 'Test questionnaire',
          Variable: 'survey_variable',
          LastEntryDate: '2026-11-03T10:38:49.13373',
          AudioRecordingEnabled: false,
          WebModeEnabled: false
        }
      ],
      QuestionnaireCount: 1,
      InterviewCount: 1,
      ResponsibleCount: 1,
      SupervisorCount: 1,
      SelectedQuestionnaire: null,
      Statuses: [{ Status: 'Completed', Count: 1 }],
      UpdatesByDay: [{ Date: '2026-11-03', Count: 1 }]
    };
  }
});
