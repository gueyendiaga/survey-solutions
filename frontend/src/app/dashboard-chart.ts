import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  ViewChild
} from '@angular/core';
import { BarChart, LineChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import * as echarts from 'echarts/core';
import type { ECharts, EChartsCoreOption } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';

echarts.use([BarChart, LineChart, GridComponent, TooltipComponent, CanvasRenderer]);

@Component({
  selector: 'app-dashboard-chart',
  standalone: true,
  template: '<div #chart class="chart-canvas w-full" [style.height.px]="height"></div>',
  styles: [
    `
      :host {
        display: block;
        width: 100%;
      }
      .chart-canvas {
        width: 100%;
      }
    `
  ]
})
export class DashboardChartComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input({ required: true }) options!: EChartsCoreOption;
  @Input() height = 300;
  @ViewChild('chart', { static: true }) private chartElement!: ElementRef<HTMLDivElement>;

  private chart?: ECharts;
  private readonly onResize = () => this.chart?.resize();

  ngAfterViewInit(): void {
    this.chart = echarts.init(this.chartElement.nativeElement);
    this.chart.setOption(this.options, true);
    window.addEventListener('resize', this.onResize);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['options'] && this.chart) {
      this.chart.setOption(this.options, true);
    }
    if (changes['height'] && this.chart) {
      this.chart.resize();
    }
  }

  ngOnDestroy(): void {
    window.removeEventListener('resize', this.onResize);
    this.chart?.dispose();
  }
}
