import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { App } from './app';
import { routes } from './app.routes';
import { DashboardChartComponent } from './shared/components/dashboard-chart/dashboard-chart';
import {
  BASE, QUESTIONNAIRE_ID, analyticsResponse, completenessResponse, dashboardResponse, fieldworkResponse,
  filterOptionsResponse, ageQuestion, syncResponse
} from './testing/fixtures';

const QUESTIONNAIRE_URL = `/questionnaires/${QUESTIONNAIRE_ID}/1`;

describe('Application routée', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes, withComponentInputBinding()), provideHttpClient(), provideHttpClientTesting()]
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify());

  function page(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  function url(): string {
    return TestBed.inject(Router).url;
  }

  /** Requêtes émises à l'ouverture d'un questionnaire sur le suivi terrain. */
  function flushQuestionnaireOpening(tab = 'suivi-terrain'): void {
    http.expectOne(`${BASE}/sync`).flush(syncResponse());
    http.expectOne(`${BASE}/filter-options`).flush(filterOptionsResponse());
    if (tab === 'suivi-terrain') {
      http.expectOne((request) => request.url === `${BASE}/fieldwork`).flush(fieldworkResponse());
    }
    http.expectOne(`${BASE}/reports`).flush([
      { id: 7, generatedAt: '2026-11-09T07:00:00Z', trigger: 'SCHEDULED', filename: 'rapport.pdf', sizeBytes: 51200 }
    ]);
    http.expectOne(`${BASE}/question-list`).flush([ageQuestion()]);
    harness.detectChanges();
    http.expectOne((request) => request.url === `${BASE}/question-completeness`).flush(completenessResponse());
    http.expectOne((request) => request.url === `${BASE}/question-analytics`
      && request.params.get('variableName') === 'age').flush(analyticsResponse());
    harness.detectChanges();
  }

  async function openQuestionnaire(tab = 'suivi-terrain'): Promise<void> {
    await harness.navigateByUrl(`${QUESTIONNAIRE_URL}/${tab}`);
    http.expectOne('/api/dashboard').flush(dashboardResponse());
    flushQuestionnaireOpening(tab);
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  it('affiche le tableau de bord à la racine', async () => {
    await harness.navigateByUrl('/');
    http.expectOne('/api/dashboard').flush(dashboardResponse());
    harness.detectChanges();

    expect(page().querySelector('h1')?.textContent).toContain('Tableau de bord');
    expect(page().querySelector('.kpis')?.textContent).toContain('Questionnaires');
  });

  it('ouvre un questionnaire depuis le catalogue sur le suivi terrain', async () => {
    await harness.navigateByUrl('/questionnaires');
    http.expectOne('/api/dashboard').flush(dashboardResponse());
    harness.detectChanges();
    expect(page().querySelector('tbody')?.textContent).toContain('Test questionnaire');
    expect(page().querySelector('tbody')?.textContent).toContain('survey_variable');

    (page().querySelector('.open-questionnaire-button') as HTMLAnchorElement).click();
    await harness.fixture.whenStable();
    harness.detectChanges();
    flushQuestionnaireOpening();

    expect(url()).toBe(`${QUESTIONNAIRE_URL}/suivi-terrain`);
    expect(page().querySelector('.qh-title')?.textContent).toContain('Test questionnaire');
    expect(page().querySelector('.sync-line')?.textContent).toContain('2 interview(s)');
    expect(page().querySelector('[role="tab"][aria-selected="true"]')?.textContent).toContain('Suivi terrain');
    expect(page().querySelector('app-fieldwork-panel .kpis')?.textContent).toContain('66,7 %');
  });

  it('reflète l’onglet actif dans l’URL et corrige un onglet inconnu', async () => {
    await openQuestionnaire();
    const tabs = page().querySelectorAll<HTMLButtonElement>('[role="tab"]');

    tabs[3].click();
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(url()).toBe(`${QUESTIONNAIRE_URL}/rapports`);
    expect(page().querySelector('#panel-reports')?.textContent).toContain('rapport.pdf');

    await harness.navigateByUrl(`${QUESTIONNAIRE_URL}/inconnu`);
    await harness.fixture.whenStable();
    harness.detectChanges();
    http.match(() => true).forEach((request) => request.flush(null));
    expect(url()).toBe(`${QUESTIONNAIRE_URL}/suivi-terrain`);
  });

  it('redirige l’adresse sans onglet vers le suivi terrain', async () => {
    await harness.navigateByUrl(QUESTIONNAIRE_URL);
    http.expectOne('/api/dashboard').flush(dashboardResponse());
    flushQuestionnaireOpening();
    expect(url()).toBe(`${QUESTIONNAIRE_URL}/suivi-terrain`);
  });

  it('signale un questionnaire introuvable', async () => {
    await harness.navigateByUrl('/questionnaires/inconnu/9/variables');
    http.expectOne('/api/dashboard').flush(dashboardResponse());
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(page().querySelector('h1')?.textContent).toContain('Questionnaire introuvable');
  });

  it('affiche les indicateurs, la distribution et les réponses d’une variable', async () => {
    await openQuestionnaire('variables');
    const content = page();

    expect(content.querySelector('.question-list-item.selected')?.textContent).toContain('age');
    expect(content.querySelector('.question-head .quality-status')?.textContent).toContain('À examiner');
    expect(content.querySelector('.question-kpi-grid')?.textContent).toContain('100 %');
    expect(content.querySelector('.distribution-table')?.textContent).toContain('20');
    expect(content.querySelector('.special-values-panel')?.textContent).toContain('Ne sait pas');
    expect(content.querySelector('.interview-key')?.textContent).toContain('01-50-90-18');

    const pie = content.querySelector('[aria-label="Afficher un graphique circulaire"]') as HTMLButtonElement;
    expect(pie.getAttribute('aria-pressed')).toBe('false');
    pie.click();
    harness.detectChanges();
    expect(pie.getAttribute('aria-pressed')).toBe('true');

    const chart = harness.fixture.debugElement.queryAll(By.directive(DashboardChartComponent)).at(-1)!
      .componentInstance as DashboardChartComponent;
    spyOn(chart, 'download');
    (content.querySelector('[aria-label="Télécharger le graphique en PNG"]') as HTMLButtonElement).click();
    expect(chart.download).toHaveBeenCalled();
  });

  it('ouvre une variable depuis l’écran qualité', async () => {
    await openQuestionnaire('qualite');
    const row = page().querySelector('tbody tr') as HTMLElement;
    expect(page().querySelector('.kpis')?.textContent).toContain('50 %');
    expect(row.textContent).toContain('age');
    expect(row.querySelector('.quality-status')?.textContent).toContain('À examiner');

    (row.querySelector('button.link') as HTMLButtonElement).click();
    await harness.fixture.whenStable();
    harness.detectChanges();
    http.match((request) => request.url === `${BASE}/question-analytics`).forEach((r) => r.flush(analyticsResponse()));
    harness.detectChanges();
    expect(url()).toBe(`${QUESTIONNAIRE_URL}/variables`);
  });

  it('transmet les filtres appliqués au suivi terrain, à la complétude et aux analyses', async () => {
    await openQuestionnaire();
    (page().querySelector('.filter-chips .chip') as HTMLButtonElement).click();
    harness.detectChanges();
    await harness.fixture.whenStable();

    const interviewer = page().querySelector('select[name="interviewer"]') as HTMLSelectElement;
    interviewer.value = interviewer.options[1].value;
    interviewer.dispatchEvent(new Event('change'));
    const from = page().querySelector('input[name="from"]') as HTMLInputElement;
    from.value = '2026-11-01';
    from.dispatchEvent(new Event('input'));
    await harness.fixture.whenStable();
    harness.detectChanges();
    (page().querySelector('.filter-apply') as HTMLButtonElement).click();
    harness.detectChanges();

    const fieldwork = http.expectOne((request) => request.url === `${BASE}/fieldwork`);
    expect(fieldwork.request.params.get('interviewer')).toBe('enq1');
    expect(fieldwork.request.params.get('from')).toBe('2026-11-01');
    expect(fieldwork.request.params.has('statuses')).toBeFalse();
    fieldwork.flush({ ...fieldworkResponse(), filtersApplied: true });
    http.expectOne((request) => request.url === `${BASE}/question-completeness`
      && request.params.get('interviewer') === 'enq1').flush(completenessResponse());
    http.expectOne((request) => request.url === `${BASE}/question-analytics`
      && request.params.get('from') === '2026-11-01').flush(analyticsResponse());
    harness.detectChanges();

    expect(page().querySelector('.fieldwork-note')?.textContent).toContain('Filtres actifs');
    expect(page().querySelector('.filter-bar')).toBeNull();
    expect(page().querySelector('.filter-chips')?.textContent).toContain('Enquêteur : enq1');
  });

  it('télécharge le rapport PDF et les exports CSV', async () => {
    spyOn(URL, 'createObjectURL').and.returnValue('blob:export');
    spyOn(URL, 'revokeObjectURL');
    spyOn(HTMLAnchorElement.prototype, 'click').and.callThrough();
    await openQuestionnaire('rapports');
    const content = page();
    expect(content.textContent).toContain('rapport.pdf');

    (content.querySelector('.report-button') as HTMLButtonElement).click();
    http.expectOne((request) => request.url.endsWith('/report.pdf'))
      .flush(new Blob(['%PDF-'], { type: 'application/pdf' }));
    harness.detectChanges();

    (content.querySelector('[aria-label="Exporter les réponses"]') as HTMLButtonElement).click();
    harness.detectChanges();
    const csv = [...content.querySelectorAll<HTMLButtonElement>('.export-button')]
      .find((button) => button.textContent?.includes('CSV'))!;
    csv.click();
    const exportRequest = http.expectOne((request) => request.url === `${BASE}/export`
      && request.params.get('format') === 'csv');
    expect(exportRequest.request.responseType).toBe('blob');
    exportRequest.flush(new Blob(['csv data'], { type: 'text/csv' }));
    await harness.fixture.whenStable();
    harness.detectChanges();

    expect(URL.createObjectURL).toHaveBeenCalledTimes(2);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:export');
    expect(content.querySelector('.menu-list')).toBeNull();
  });
});

describe('App (coquille)', () => {
  it('propose le tableau de bord et les questionnaires, sans entrée « Récent »', async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes, withComponentInputBinding()), provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const nav = (fixture.nativeElement as HTMLElement).querySelector('nav.sidebar') as HTMLElement;

    expect([...nav.querySelectorAll('.nav-item')].map((item) => item.textContent?.trim()))
      .toEqual(['Tableau de bord', 'Questionnaires']);
    expect(nav.textContent).not.toContain('Récent');
    expect(nav.querySelector('.nav-item[href="/questionnaires"]')).not.toBeNull();
  });
});

describe('Attente de l’import des données', () => {
  afterEach(() => jasmine.clock().uninstall());

  it('attend la fin de l’import puis recharge les indicateurs', async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes, withComponentInputBinding()), provideHttpClient(), provideHttpClientTesting()]
    });
    const http = TestBed.inject(HttpTestingController);
    const pending = { status: 409, statusText: 'Conflict' };
    const harness = await RouterTestingHarness.create();
    jasmine.clock().install();
    await harness.navigateByUrl(`${QUESTIONNAIRE_URL}/suivi-terrain`);
    http.expectOne('/api/dashboard').flush(dashboardResponse());
    http.expectOne(`${BASE}/sync`).flush({ inProgress: false, lastSuccessAt: null, interviewCount: null, lastStatus: null, lastError: null });
    http.expectOne(`${BASE}/filter-options`).flush({ supervisors: [], interviewers: [], statuses: [] });
    http.expectOne((request) => request.url === `${BASE}/fieldwork`).flush({ detail: 'Synchronisation en cours', syncPending: true }, pending);
    http.expectOne(`${BASE}/reports`).flush([]);
    http.expectOne(`${BASE}/question-list`).flush([ageQuestion()]);
    harness.detectChanges();
    http.expectOne((request) => request.url === `${BASE}/question-completeness`).flush({ syncPending: true }, pending);
    http.expectOne((request) => request.url === `${BASE}/question-analytics`).flush({ syncPending: true }, pending);
    harness.detectChanges();
    const page = harness.routeNativeElement as HTMLElement;

    expect(page.querySelector('.sync-line')?.textContent).toContain('Synchronisation en cours');
    expect(page.querySelector('.alert')).toBeNull();
    expect(page.textContent).toContain('Import des données en cours');

    jasmine.clock().tick(3000);
    http.expectOne(`${BASE}/sync`).flush(syncResponse());
    harness.detectChanges();
    http.expectOne(`${BASE}/filter-options`).flush({ supervisors: [], interviewers: [], statuses: [] });
    http.expectOne((request) => request.url === `${BASE}/fieldwork`).flush(fieldworkResponse());
    http.expectOne((request) => request.url === `${BASE}/question-completeness`).flush(completenessResponse());
    http.expectOne((request) => request.url === `${BASE}/question-analytics`).flush(analyticsResponse());
    harness.detectChanges();
    expect(page.querySelector('.sync-line')?.textContent).toContain('2 interview(s)');
    http.verify();
  });
});
