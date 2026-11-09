import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { By } from '@angular/platform-browser';
import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { DashboardChartComponent } from './dashboard-chart';

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

  it('renders dashboard totals and questionnaire metadata from HQ', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    http.expectOne('/api/dashboard').flush(dashboardResponse());
    fixture.detectChanges();

    const content = fixture.nativeElement as HTMLElement;
    expect(content.querySelector('.kpi-grid')?.textContent).toContain('1');
    expect(content.querySelector('.data-caption')?.textContent).toContain('03/11/2026');
    expect(content.querySelector('tbody')?.textContent).toContain('Test questionnaire');
    expect(content.querySelector('tbody')?.textContent).toContain('survey_variable');
  });

  it('shows variable completeness and loads analytics for a selected variable', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    http.expectOne('/api/dashboard').flush(dashboardResponse());
    fixture.detectChanges();

    const catalogLink = fixture.nativeElement.querySelector(
      'a[href="#questionnaires"]'
    ) as HTMLAnchorElement;
    catalogLink.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('h1')?.textContent).toContain('Questionnaires');

    const selectButton = fixture.nativeElement.querySelector(
      '.open-questionnaire-button'
    ) as HTMLButtonElement;
    selectButton.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('h1')?.textContent).toContain('Questions & réponses');
    http.expectOne(
      '/api/questionnaires/588daf2d-0c72-4b89-bec9-254459a68983/1/question-list'
    ).flush([
      {
        id: 'question-1',
        variableName: 'age',
        questionText: 'Quel âge ?',
        label: 'Âge',
        type: 'Numeric',
        breadcrumbs: [],
        optionCount: 0
      }
    ]);
    fixture.detectChanges();

    http.expectOne(
      '/api/questionnaires/588daf2d-0c72-4b89-bec9-254459a68983/1/question-completeness'
    ).flush({
      interviewCount: 2,
      variables: [
        {
          question: {
            id: 'question-1',
            variableName: 'age',
            questionText: 'Quel âge ?',
            label: 'Âge',
            type: 'Numeric',
            breadcrumbs: [],
            optionCount: 0
          },
          answeredInterviewCount: 1,
          interviewWithoutAnswerCount: 1,
          responseRate: 50
        }
      ]
    });
    fixture.detectChanges();

    http.expectOne(
      (req) =>
        req.url ===
          '/api/questionnaires/588daf2d-0c72-4b89-bec9-254459a68983/1/question-analytics' &&
        req.params.get('variableName') === 'age'
    ).flush({
      question: {
        id: 'question-1',
        variableName: 'age',
        questionText: 'Quel âge ?',
        label: 'Âge',
        type: 'Numeric',
        breadcrumbs: [],
        optionCount: 0
      },
      interviewCount: 1,
      answeredInterviewCount: 1,
      interviewWithoutAnswerCount: 0,
      responseCount: 1,
      observations: [{ interviewNumber: 1, value: '20' }],
      distribution: [{ label: '20', count: 1, percentage: 100 }],
      minimum: 20,
      maximum: 20,
      mean: 20,
      median: 20,
      sum: 20,
      percentile05: 20,
      percentile95: 20,
      nonNumericResponseCount: 0
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.question-list-item.selected')?.textContent)
      .toContain('age');
    expect(fixture.nativeElement.querySelector('.selected-completeness')?.textContent).toContain('50');
    expect(fixture.nativeElement.querySelector('.quality-status')?.textContent).toContain('À examiner');
    expect(fixture.nativeElement.querySelector('.question-list-item')?.textContent).toContain('age');
    expect(fixture.nativeElement.querySelector('.question-kpi-grid')?.textContent).toContain('20');
    expect(fixture.nativeElement.querySelector('.distribution-table')?.textContent).toContain('100');
    const pieChartButton = fixture.nativeElement.querySelector(
      '[aria-label="Afficher un graphique circulaire"]'
    ) as HTMLButtonElement;
    expect(pieChartButton.getAttribute('aria-pressed')).toBe('false');
    pieChartButton.click();
    fixture.detectChanges();
    expect(pieChartButton.getAttribute('aria-pressed')).toBe('true');
    const horizontalBarChartButton = fixture.nativeElement.querySelector(
      '[aria-label="Afficher un graphique en barres horizontales"]'
    ) as HTMLButtonElement;
    expect(horizontalBarChartButton.getAttribute('aria-pressed')).toBe('false');
    horizontalBarChartButton.click();
    fixture.detectChanges();
    expect(horizontalBarChartButton.getAttribute('aria-pressed')).toBe('true');
    const chart = fixture.debugElement.query(
      By.directive(DashboardChartComponent)
    ).componentInstance as DashboardChartComponent;
    expect(chart.downloadFilename).toBe('repartition-age');
    const downloadSpy = spyOn(chart, 'download');
    const downloadButton = fixture.nativeElement.querySelector(
      '[aria-label="Télécharger le graphique en PNG"]'
    ) as HTMLButtonElement;
    downloadButton.click();
    expect(downloadSpy).toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.response-list-panel')?.textContent).toContain('20');
    expect(fixture.nativeElement.querySelector('.response-list-panel')?.textContent).toContain('Interview 1');
  });

  it('lets the questions navigation link open the catalog when no questionnaire is selected', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    http.expectOne('/api/dashboard').flush(dashboardResponse());
    fixture.detectChanges();

    const questionsLink = fixture.nativeElement.querySelector(
      'a[href="#questions"]'
    ) as HTMLAnchorElement;
    expect(questionsLink.getAttribute('aria-disabled')).toBeNull();
    questionsLink.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('h1')?.textContent).toContain('Questionnaires');
    expect(fixture.nativeElement.querySelector('.open-questionnaire-button')).not.toBeNull();
  });

  it('downloads the selected questionnaire responses as CSV', () => {
    spyOn(URL, 'createObjectURL').and.returnValue('blob:export');
    spyOn(URL, 'revokeObjectURL');

    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    http.expectOne('/api/dashboard').flush(dashboardResponse());
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('a[href="#questionnaires"]') as HTMLAnchorElement).click();
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.open-questionnaire-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    http.expectOne(
      '/api/questionnaires/588daf2d-0c72-4b89-bec9-254459a68983/1/question-list'
    ).flush([
      {
        id: 'question-1',
        variableName: 'age',
        questionText: 'Quel âge ?',
        label: 'Âge',
        type: 'Numeric',
        breadcrumbs: [],
        optionCount: 0
      }
    ]);
    fixture.detectChanges();
    http.expectOne(
      '/api/questionnaires/588daf2d-0c72-4b89-bec9-254459a68983/1/question-completeness'
    ).flush({ interviewCount: 0, variables: [] });
    fixture.detectChanges();
    http.expectOne(
      (request) => request.url.endsWith('/question-analytics') &&
        request.params.get('variableName') === 'age'
    ).flush({
      question: {
        id: 'question-1',
        variableName: 'age',
        questionText: 'Quel âge ?',
        label: 'Âge',
        type: 'Numeric',
        breadcrumbs: [],
        optionCount: 0
      },
      interviewCount: 0,
      answeredInterviewCount: 0,
      interviewWithoutAnswerCount: 0,
      responseCount: 0,
      observations: [],
      distribution: [],
      minimum: null,
      maximum: null,
      mean: null,
      median: null,
      sum: null,
      percentile05: null,
      percentile95: null,
      nonNumericResponseCount: 0
    });
    fixture.detectChanges();

    spyOn(HTMLAnchorElement.prototype, 'click');
    (fixture.nativeElement.querySelector('.export-button') as HTMLButtonElement).click();
    const exportRequest = http.expectOne(
      (request) => request.url ===
        '/api/questionnaires/588daf2d-0c72-4b89-bec9-254459a68983/1/export' &&
        request.params.get('format') === 'csv'
    );
    expect(exportRequest.request.responseType).toBe('blob');
    exportRequest.flush(new Blob(['csv data'], { type: 'text/csv' }));
    fixture.detectChanges();

    expect(URL.createObjectURL).toHaveBeenCalled();
    expect(HTMLAnchorElement.prototype.click).toHaveBeenCalled();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:export');
  });

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
