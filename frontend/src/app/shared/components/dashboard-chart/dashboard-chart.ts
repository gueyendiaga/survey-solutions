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
import { BarChart, LineChart, PieChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import * as echarts from 'echarts/core';
import type { ECharts, EChartsCoreOption } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';

echarts.use([BarChart, LineChart, PieChart, GridComponent, TooltipComponent, CanvasRenderer]);

@Component({
  selector: 'app-dashboard-chart',
  standalone: true,
  templateUrl: './dashboard-chart.html',
  styleUrl: './dashboard-chart.css'
})
export class DashboardChartComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input({ required: true }) options!: EChartsCoreOption;
  @Input() height = 300;
  @Input() downloadFilename = 'graphique';
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

  download(): void {
    if (!this.chart) {
      return;
    }

    const link = document.createElement('a');
    link.href = this.chart.getDataURL({
      type: 'png',
      pixelRatio: 2,
      backgroundColor: '#ffffff'
    });
    link.download = `${this.downloadFilename}.png`;
    link.click();
  }
}
