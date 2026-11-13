import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Questionnaire } from '../../../core/models/dashboard.models';
import { QuestionnaireHeaderComponent } from './questionnaire-header';

describe('QuestionnaireHeaderComponent', () => {
  let fixture: ComponentFixture<QuestionnaireHeaderComponent>;
  let header: QuestionnaireHeaderComponent;

  const questionnaire = {
    questionnaireIdentity: 'q$1', questionnaireId: 'q', version: 1, title: 'Enquête ménages', variable: 'menages',
    lastEntryDate: null, webModeEnabled: false
  } as unknown as Questionnaire;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [QuestionnaireHeaderComponent] }).compileComponents();
    fixture = TestBed.createComponent(QuestionnaireHeaderComponent);
    header = fixture.componentInstance;
    fixture.componentRef.setInput('questionnaire', questionnaire);
    fixture.componentRef.setInput('active', 'fieldwork');
    fixture.componentRef.setInput('badges', { review: 0, variables: 12, quality: 3, reports: 0 });
    fixture.detectChanges();
  });

  function tabs(): HTMLButtonElement[] {
    return [...(fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('[role="tab"]')];
  }

  it('exposes accessible tabs with badges and a single tab stop', () => {
    expect(tabs().map((tab) => tab.textContent?.replace(/\d+/g, '').trim()))
      .toEqual(['Suivi terrain', 'Revue', 'Variables', 'Qualité des données', 'Rapports']);
    expect(tabs()[0].getAttribute('aria-selected')).toBe('true');
    expect(tabs()[0].getAttribute('aria-controls')).toBe('panel-fieldwork');
    expect(tabs().map((tab) => tab.tabIndex)).toEqual([0, -1, -1, -1, -1]);
    expect(tabs()[2].querySelector('.qh-badge')?.textContent).toContain('12');
    expect(tabs()[3].querySelector('.qh-badge.warn')?.getAttribute('aria-label')).toBe('3 variable(s) à examiner');
    expect(tabs()[4].querySelector('.qh-badge')).toBeNull();
  });

  it('moves focus with arrow keys and activates a tab on click only', () => {
    const changes: string[] = [];
    header.tabChange.subscribe((tab) => changes.push(tab));
    const list = (fixture.nativeElement as HTMLElement).querySelector('[role="tablist"]') as HTMLElement;

    tabs()[0].focus();
    list.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
    fixture.detectChanges();
    expect(document.activeElement).toBe(tabs()[4]);
    list.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true }));
    fixture.detectChanges();
    expect(document.activeElement).toBe(tabs()[0]);
    expect(changes).toEqual([]);

    tabs()[3].click();
    tabs()[0].click();
    expect(changes).toEqual(['quality']);
  });

  it('opens the export menu and closes it on Escape or outside click', () => {
    const formats: string[] = [];
    header.exportData.subscribe((format) => formats.push(format));
    const element = fixture.nativeElement as HTMLElement;
    const toggle = element.querySelector('[aria-label="Exporter les réponses"]') as HTMLButtonElement;

    toggle.click();
    fixture.detectChanges();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(element.querySelector('.menu-list')).toBeNull();

    toggle.click();
    fixture.detectChanges();
    document.body.click();
    fixture.detectChanges();
    expect(element.querySelector('.menu-list')).toBeNull();

    toggle.click();
    fixture.detectChanges();
    (element.querySelectorAll('.export-button')[1] as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(formats).toEqual(['xlsx']);
    expect(element.querySelector('.menu-list')).toBeNull();
  });
});
