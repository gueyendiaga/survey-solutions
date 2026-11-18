import { TestBed } from '@angular/core/testing';
import { FieldOperations } from '../../../../core/models/dashboard.models';
import { operationsResponse } from '../../../../testing/fixtures';
import { FieldOperationsComponent } from './field-operations';

describe('FieldOperationsComponent', () => {
  async function render(operations = operationsResponse()) {
    await TestBed.configureTestingModule({ imports: [FieldOperationsComponent] }).compileComponents();
    const fixture = TestBed.createComponent(FieldOperationsComponent);
    fixture.componentRef.setInput('operations', operations as FieldOperations);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('summarises tablets, validation backlog, appointments and collection modes', async () => {
    const element = (await render()).nativeElement as HTMLElement;

    const kpis = element.querySelector('.ops-kpis')!.textContent!;
    expect(kpis).toContain('1 / 2');
    expect(kpis).toContain('1 jamais actif(s)');
    expect(kpis).toContain('dont 2 depuis plus de 3 jours');
    expect(element.querySelector('.validation')!.textContent).toContain('1,3 j');
    expect(element.querySelector('.waiting')!.textContent).toContain('01-50-90-18');
    expect(element.querySelector('.appts li.late')!.textContent).toContain('Rappeler');
    expect(element.querySelector('.modes')!.textContent).toContain('Web (CAWI)');
  });

  it('lists interviewers needing attention first', async () => {
    const element = (await render()).nativeElement as HTMLElement;
    const rows = [...element.querySelectorAll('.tablet-table tbody tr')];

    expect(rows[0].getAttribute('data-state')).toBe('NEVER');
    expect(rows[0].textContent).toContain('Jamais actif');
    expect(rows[0].textContent).toContain('Aucune');
    expect(rows[1].textContent).toContain('enq1');
  });

  it('breaks progress down by zone and emits the chosen variable', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;
    const chosen: (string | null)[] = [];
    fixture.componentInstance.zoneVariableChange.subscribe((value) => chosen.push(value));

    expect(element.querySelector('.zone-table tbody')!.textContent).toContain('Dakar');
    expect(element.querySelector('.zone-table tbody')!.textContent).toContain('66,7 %');
    const picker = element.querySelector('app-search-select[name="zoneVariable"]') as HTMLElement;
    expect(picker.querySelector('.select-trigger')!.textContent).toContain('Région (region) · identification');
    (picker.querySelector('.select-trigger') as HTMLButtonElement).click();
    fixture.detectChanges();
    const milieu = [...picker.querySelectorAll<HTMLButtonElement>('[role="option"]')]
      .find((option) => option.textContent!.includes('Milieu'))!;
    milieu.click();
    expect(chosen).toEqual(['milieu']);
  });

  it('hides the collection mode when every interview uses tablets', async () => {
    const operations = { ...operationsResponse(), modes: [{ mode: 'CAPI', count: 4 }] };
    const element = (await render(operations)).nativeElement as HTMLElement;
    expect(element.querySelector('.modes')).toBeNull();
  });
});
