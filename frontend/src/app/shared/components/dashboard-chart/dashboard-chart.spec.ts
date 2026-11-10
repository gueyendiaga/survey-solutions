import { TestBed } from '@angular/core/testing';
import type { ECharts } from 'echarts/core';
import { QuestionAnalytics } from '../../../core/models/dashboard.models';
import { answerOptions } from '../../utils/chart-options';
import { DashboardChartComponent } from './dashboard-chart';

describe('DashboardChartComponent', () => {
  const longLabel = 'Revenus insuffisants pour payer les cotisations mensuelles';

  function analytics(): QuestionAnalytics {
    const labels = [longLabel, 'Autre raison', 'Charges familiales', 'Convictions personnelles', 'Manque d’information',
      'Peur de perdre ses droits', 'Procédures trop longues', 'Taux de cotisation trop élevé'];
    return { distribution: labels.map((label, index) => ({ label, count: index ? 0 : 2, percentage: index ? 0 : 100 })) } as QuestionAnalytics;
  }

  it('shows the full option label when the mouse is over its shortened axis label', async () => {
    await TestBed.configureTestingModule({ imports: [DashboardChartComponent] }).compileComponents();
    const fixture = TestBed.createComponent(DashboardChartComponent);
    (fixture.nativeElement as HTMLElement).style.width = '720px';
    document.body.appendChild(fixture.nativeElement);
    fixture.componentRef.setInput('options', answerOptions(analytics(), 'bar'));
    fixture.detectChanges();

    const chart = (fixture.componentInstance as unknown as { chart: ECharts }).chart;
    const zr = chart.getZr() as unknown as {
      storage: { getDisplayList(update: boolean): { style?: { text?: string }; getBoundingRect(): { x: number; y: number; width: number; height: number }; transform?: number[] }[] };
      handler: { dispatch(type: string, event: object): void };
    };
    const label = zr.storage.getDisplayList(true).find((element) => element.style?.text?.startsWith('Revenus'))!;
    expect(label.style!.text).not.toBe(longLabel);
    const box = label.getBoundingRect();
    const t = label.transform ?? [1, 0, 0, 1, 0, 0];
    const cx = box.x + box.width / 2;
    const cy = box.y + box.height / 2;
    const point = { zrX: t[0] * cx + t[2] * cy + t[4], zrY: t[1] * cx + t[3] * cy + t[5] };

    zr.handler.dispatch('mousemove', { ...point, offsetX: point.zrX, offsetY: point.zrY });
    fixture.detectChanges();
    const tip = (fixture.nativeElement as HTMLElement).querySelector('.axis-label-tip') as HTMLElement;
    expect(tip?.textContent?.trim()).toBe(longLabel);

    zr.handler.dispatch('mousemove', { zrX: 360, zrY: 100, offsetX: 360, offsetY: 100 });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.axis-label-tip')).toBeNull();
    fixture.nativeElement.remove();
  });
});
