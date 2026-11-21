import { Component, Input, OnChanges, OnInit, signal } from '@angular/core';
import type { EChartsCoreOption } from 'echarts/core';
import { MapChart, ScatterChart } from 'echarts/charts';
import { GeoComponent, VisualMapComponent } from 'echarts/components';
import * as echarts from 'echarts/core';
import { DashboardChartComponent } from '../dashboard-chart/dashboard-chart';
import { SENEGAL_REGIONS } from '../../utils/senegal-regions';

/** Valeur d'un indicateur pour une région (name = nom officiel, voir SENEGAL_REGIONS). */
export interface RegionValue {
  name: string;
  value: number | null;
  /** Lignes supplémentaires de l'infobulle (déjà échappées si elles viennent de données HQ). */
  details?: string[];
}

// Modules de carte enregistrés ici (chunk de la page questionnaire) et non dans le graphique
// commun, pour ne pas alourdir le bundle initial.
echarts.use([MapChart, ScatterChart, GeoComponent, VisualMapComponent]);

const MAP_NAME = 'senegal-regions';
type Ring = [number, number][];
interface RegionFeature {
  properties: { name: string };
  geometry: { type: 'Polygon' | 'MultiPolygon'; coordinates: Ring[] | Ring[][] };
}

/** Centre approximatif de chaque région (moyenne des sommets du plus grand contour). */
const centroids = new Map<string, [number, number]>();
let geoJsonLoad: Promise<void> | null = null;

/** Charge le fond de carte (chunk séparé) et l'enregistre auprès d'ECharts, une seule fois. */
function loadSenegalMap(): Promise<void> {
  geoJsonLoad ??= import('./senegal-regions.geo.json').then((module) => {
    const geoJson = ((module as { default?: object }).default ?? module) as { features: RegionFeature[] };
    for (const feature of geoJson.features) {
      const polygons = (feature.geometry.type === 'Polygon' ? [feature.geometry.coordinates] : feature.geometry.coordinates) as Ring[][];
      const ring = polygons.map((polygon) => polygon[0]).sort((a, b) => b.length - a.length)[0];
      const sum = ring.reduce((acc, [x, y]) => [acc[0] + x, acc[1] + y], [0, 0]);
      centroids.set(feature.properties.name, [sum[0] / ring.length, sum[1] / ring.length]);
    }
    echarts.registerMap(MAP_NAME, geoJson as unknown as Parameters<typeof echarts.registerMap>[1]);
  });
  return geoJsonLoad;
}

/**
 * Carte choroplèthe des 14 régions du Sénégal. Fond de carte : geoBoundaries SEN ADM1
 * (Gouvernement du Sénégal / OCHA ROWCA, licence CC BY 3.0 IGO), simplifié et embarqué.
 */
@Component({
  selector: 'app-region-map',
  standalone: true,
  imports: [DashboardChartComponent],
  template: `
    @if (options(); as chartOptions) {
      <app-dashboard-chart [options]="chartOptions" [height]="height" [downloadFilename]="downloadFilename" />
    } @else {
      <div class="map-loading" [style.height.px]="height">Chargement de la carte…</div>
    }
    <p class="map-source">Fond de carte : geoBoundaries (Gouvernement du Sénégal, OCHA), CC BY 3.0 IGO.</p>
  `,
  styles: `
    :host { display: block; }
    .map-loading { display: grid; place-items: center; color: var(--text-2); font-size: var(--fs-small); }
    .map-source { margin: 4px 0 0; color: #9ca3af; font-size: 11px; text-align: right; }
  `
})
export class RegionMapComponent implements OnInit, OnChanges {
  @Input({ required: true }) values: RegionValue[] = [];
  /** Nom de l'indicateur (légende et infobulle). */
  @Input() metric = 'Interviews';
  @Input() unit = '';
  @Input() height = 420;
  @Input() downloadFilename = 'carte-regions';

  protected readonly options = signal<EChartsCoreOption | null>(null);
  private ready = false;

  ngOnInit(): void {
    loadSenegalMap().then(() => {
      this.ready = true;
      this.render();
    });
  }

  ngOnChanges(): void {
    this.render();
  }

  private render(): void {
    if (!this.ready) {
      return;
    }
    const byName = new Map(this.values.map((item) => [item.name, item]));
    const numbers = this.values.map((item) => item.value).filter((value): value is number => value !== null);
    const max = Math.max(1, ...numbers);
    const min = numbers.length > 1 ? Math.min(...numbers) : 0;
    const format = (value: number) => `${value.toLocaleString('fr-FR', { maximumFractionDigits: 1 })}${this.unit ? ' ' + this.unit : ''}`;
    this.options.set({
      animation: false,
      textStyle: { fontFamily: 'Inter, sans-serif' },
      aria: { enabled: true, label: { description: `Carte des régions du Sénégal : ${this.metric}` } },
      tooltip: {
        trigger: 'item',
        backgroundColor: '#ffffff',
        borderColor: '#e5e7eb',
        textStyle: { color: '#1f2937', fontSize: 13 },
        extraCssText: 'box-shadow: 0 8px 24px rgba(16, 24, 40, 0.12); border-radius: 10px;',
        formatter: (params: { name: string }) => {
          const item = byName.get(params.name);
          const value = item?.value;
          const lines = [`<strong>${params.name}</strong>`, `${this.metric} : <strong>${value === null || value === undefined ? 'aucune donnée' : format(value)}</strong>`];
          return [...lines, ...(item?.details ?? [])].join('<br/>');
        }
      },
      visualMap: {
        type: 'continuous',
        seriesIndex: 0,
        min,
        max: Math.max(max, min + 1),
        orient: 'horizontal',
        left: 'center',
        bottom: 0,
        itemWidth: 10,
        itemHeight: 160,
        calculable: false,
        text: [format(Math.max(max, min + 1)), format(min)],
        textStyle: { color: '#4b5563', fontSize: 11 },
        inRange: { color: ['#dbeafe', '#93c5fd', '#3b82f6', '#1d4ed8', '#1e3a8a'] },
        outOfRange: { color: '#f3f4f6' }
      },
      geo: {
        map: MAP_NAME,
        roam: false,
        layoutCenter: ['50%', '46%'],
        layoutSize: '96%',
        label: { show: false },
        itemStyle: { areaColor: '#f3f4f6', borderColor: '#ffffff', borderWidth: 1.2 },
        emphasis: {
          label: { show: true, color: '#111827', fontWeight: 600 },
          itemStyle: { areaColor: '#fde68a' }
        },
        select: { disabled: true }
      },
      series: [
        {
          type: 'map',
          geoIndex: 0,
          data: SENEGAL_REGIONS.map((region) => ({
            name: region.name,
            value: byName.get(region.name)?.value ?? NaN
          }))
        },
        {
          // Pastille proportionnelle au centre des régions renseignées : Dakar reste visible.
          type: 'scatter',
          coordinateSystem: 'geo',
          z: 3,
          data: this.values
            .filter((item) => item.value !== null && centroids.has(item.name))
            .map((item) => ({ name: item.name, value: [...centroids.get(item.name)!, item.value as number] })),
          symbolSize: (value: number[]) => 10 + 16 * ((value[2] - min) / Math.max(1, max - min)),
          itemStyle: { color: '#f59e0b', borderColor: '#ffffff', borderWidth: 2, shadowBlur: 6, shadowColor: 'rgba(0,0,0,0.2)' },
          label: {
            show: true,
            position: 'right',
            formatter: (params: { name: string; value: number[] }) => `${params.name} · ${format(params.value[2])}`,
            color: '#111827',
            fontSize: 11,
            fontWeight: 600,
            textBorderColor: 'rgba(255,255,255,0.9)',
            textBorderWidth: 3
          }
        }
      ]
    });
  }
}
