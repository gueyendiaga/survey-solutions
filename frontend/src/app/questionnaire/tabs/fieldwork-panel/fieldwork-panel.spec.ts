import { TestBed } from '@angular/core/testing';
import { FieldworkOverview } from '../../../core/models/dashboard.models';
import { fieldworkResponse } from '../../../testing/fixtures';
import { FieldworkPanelComponent } from './fieldwork-panel';

describe('FieldworkPanelComponent', () => {
  it('fills days without completion so the time axis stays regular', () => {
    const days = FieldworkPanelComponent.continuousDays([
      { date: '2026-11-03', completed: 1, cumulative: 1 },
      { date: '2026-11-05', completed: 3, cumulative: 4 }
    ]);

    expect(days.map((day) => day.date)).toEqual(['2026-11-03', '2026-11-04', '2026-11-05']);
    expect(days.map((day) => day.completed)).toEqual([1, 0, 3]);
    expect(days.map((day) => day.cumulative)).toEqual([1, 1, 4]);
  });

  it('shows key figures, team statuses and attention points', async () => {
    await TestBed.configureTestingModule({ imports: [FieldworkPanelComponent] }).compileComponents();
    const fixture = TestBed.createComponent(FieldworkPanelComponent);
    fixture.componentRef.setInput('overview', fieldworkResponse() as FieldworkOverview);
    fixture.detectChanges();
    const panel = fixture.nativeElement as HTMLElement;

    expect(panel.querySelector('.kpis')?.textContent).toContain('66,7 %');
    expect(panel.querySelector('.team-row')?.textContent).toContain('sup1');
    expect(panel.querySelector('.interviewer-row.not-started')?.textContent).toContain('Pas commencé');
    expect(panel.querySelector('.attention')?.textContent).toContain('enq2 n’a pas commencé');
  });
});
