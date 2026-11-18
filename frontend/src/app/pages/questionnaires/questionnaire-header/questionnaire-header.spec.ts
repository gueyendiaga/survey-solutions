import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Questionnaire } from '../../../core/models/dashboard.models';
import { QUESTIONNAIRE_TABS } from '../questionnaire-tabs';
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

  function tab(id: string): HTMLButtonElement {
    return tabs().find((button) => button.id === `tab-${id}`)!;
  }

  it('exposes accessible tabs with badges and a single tab stop', () => {
    expect(tabs().map((button) => button.textContent?.replace(/\d+/g, '').trim()))
      .toEqual(QUESTIONNAIRE_TABS.map((definition) => definition.label));
    expect(tabs()[0].getAttribute('aria-selected')).toBe('true');
    expect(tabs()[0].getAttribute('aria-controls')).toBe('panel-fieldwork');
    expect(tabs().map((button) => button.tabIndex)).toEqual(tabs().map((_, index) => (index === 0 ? 0 : -1)));
    expect(tab('variables').querySelector('.qh-badge')?.textContent).toContain('12');
    expect(tab('quality').querySelector('.qh-badge.warn')?.getAttribute('aria-label')).toBe('3 variable(s) à examiner');
    expect(tab('reports').querySelector('.qh-badge')).toBeNull();
  });

  it('moves focus with arrow keys and activates a tab on click only', () => {
    const changes: string[] = [];
    header.tabChange.subscribe((value) => changes.push(value));
    const list = (fixture.nativeElement as HTMLElement).querySelector('[role="tablist"]') as HTMLElement;

    tabs()[0].focus();
    list.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
    fixture.detectChanges();
    expect(document.activeElement).toBe(tabs()[tabs().length - 1]);
    list.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true }));
    fixture.detectChanges();
    expect(document.activeElement).toBe(tabs()[0]);
    expect(changes).toEqual([]);

    tab('quality').click();
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
