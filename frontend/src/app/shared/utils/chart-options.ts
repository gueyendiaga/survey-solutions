import type { EChartsCoreOption } from 'echarts/core';
import { Dashboard, QuestionAnalytics } from '../../core/models/dashboard.models';
import { escapeHtml, htmlToText } from './html-text';
import { statusLabel } from './labels';

export type AnswerChartType = 'bar' | 'horizontalBar' | 'pie';

/**
 * Palette catégorielle : couleurs vives et distinctes, une par barre ou part (bleu, rouge,
 * émeraude, orange, violet, rose, cyan, lime, puis déclinaisons). Mêmes valeurs que les
 * jetons --c-* de styles.css.
 */
export const CHART_COLORS = [
  '#3b82f6', '#ef4444', '#10b981', '#f59e0b', '#8b5cf6', '#ec4899', '#06b6d4', '#84cc16',
  '#6366f1', '#f97316', '#14b8a6', '#a855f7', '#e11d48', '#0ea5e9', '#22c55e', '#eab308'
];

/** Couleur de la palette pour le rang index (cyclique). */
export function chartColor(index: number): string {
  return CHART_COLORS[((index % CHART_COLORS.length) + CHART_COLORS.length) % CHART_COLORS.length];
}

const AXIS_LABEL = { color: '#4b5563', fontSize: 12 };
const AXIS_LINE = { lineStyle: { color: '#d1d5db' } };
const SPLIT_LINE = { lineStyle: { color: '#eef0f4', type: 'dashed' as const } };
const TOOLTIP_STYLE = {
  backgroundColor: '#ffffff',
  borderColor: '#e5e7eb',
  borderWidth: 1,
  padding: [8, 12],
  textStyle: { color: '#1f2937', fontSize: 13 },
  extraCssText: 'box-shadow: 0 8px 24px rgba(16, 24, 40, 0.12); border-radius: 10px;'
};

/** Options communes : police, palette, description accessible (lecteurs d'écran). */
export function baseOptions(description: string): EChartsCoreOption {
  return {
    animation: false,
    color: CHART_COLORS,
    textStyle: { fontFamily: 'Inter, sans-serif' },
    aria: { enabled: true, label: { description } }
  };
}

/** Une couleur par barre : chaque valeur devient un point avec sa couleur et un dégradé discret. */
export function coloredBars(values: number[], horizontal = false): object[] {
  return values.map((value, index) => ({
    value,
    itemStyle: {
      color: chartColor(index),
      borderRadius: horizontal ? [0, 8, 8, 0] : [8, 8, 0, 0]
    }
  }));
}

/** Coupe un libellé en lignes de lineLength caractères au plus, avec « … » au-delà de maxLines. */
export function shortLabel(label: string, lineLength: number, maxLines: number): string {
  const words = label.trim().split(/\s+/);
  const lines: string[] = [];
  let current = '';
  for (const word of words) {
    const candidate = current ? `${current} ${word}` : word;
    if (candidate.length <= lineLength) {
      current = candidate;
      continue;
    }
    if (current) {
      lines.push(current);
    }
    current = word.length > lineLength ? `${word.slice(0, lineLength - 1)}…` : word;
  }
  if (current) {
    lines.push(current);
  }
  if (lines.length <= maxLines) {
    return lines.join('\n');
  }
  const kept = lines.slice(0, maxLines);
  const last = kept[maxLines - 1];
  kept[maxLines - 1] = `${last.length >= lineLength ? last.slice(0, lineLength - 1) : last}…`;
  return kept.join('\n');
}

/** Distribution d'une variable : colonnes, barres horizontales ou secteurs. */
export function answerOptions(analytics: QuestionAnalytics, type: AnswerChartType): EChartsCoreOption {
  const labels = analytics.distribution.map((item) => htmlToText(item.label));
  const answerTooltip = (index: number) => {
    const answer = analytics.distribution[index];
    return `${escapeHtml(htmlToText(answer.label))}<br/><strong>${answer.count.toLocaleString('fr-FR')}</strong> réponse(s) · ${answer.percentage}%`;
  };

  if (type === 'pie') {
    return {
      ...baseOptions('Répartition des réponses en secteurs'),
      tooltip: {
        ...TOOLTIP_STYLE,
        trigger: 'item' as const,
        formatter: (item: { name: string; value: number; percent: number }) =>
          `${escapeHtml(item.name)}<br/><strong>${item.value.toLocaleString('fr-FR')}</strong> réponse(s) · ${item.percent}%`
      },
      series: [
        {
          type: 'pie' as const,
          radius: '66%',
          center: ['50%', '52%'],
          data: analytics.distribution.map((item, index) => ({
            name: labels[index],
            value: item.count
          })),
          label: pieLabel(),
          labelLine: { length: 12, length2: 10, lineStyle: { width: 1 } },
          itemStyle: { borderColor: '#fff', borderWidth: 2 }
        }
      ]
    };
  }

  if (type === 'horizontalBar') {
    return {
      ...baseOptions('Répartition des réponses en barres horizontales'),
      grid: { left: 16, right: 48, top: 12, bottom: 24, containLabel: true },
      tooltip: {
        ...TOOLTIP_STYLE,
        trigger: 'axis' as const,
        axisPointer: { type: 'shadow' as const },
        formatter: (items: { dataIndex: number }[]) => answerTooltip(items[0].dataIndex)
      },
      xAxis: { type: 'value' as const, minInterval: 1, axisLabel: AXIS_LABEL, splitLine: SPLIT_LINE },
      yAxis: {
        type: 'category' as const,
        data: labels,
        triggerEvent: true,
        axisLabel: { ...AXIS_LABEL, color: '#374151', width: 160, overflow: 'truncate' as const },
        inverse: true,
        axisLine: AXIS_LINE,
        axisTick: { show: false }
      },
      series: [
        {
          type: 'bar' as const,
          data: coloredBars(analytics.distribution.map((item) => item.count), true),
          barMaxWidth: 28,
          label: valueLabel('right')
        }
      ]
    };
  }

  // Libellés courts sous les barres : les modalités longues (phrases entières) écrasaient
  // sinon la zone de tracé. Le libellé complet reste dans l'infobulle et le tableau.
  const categoryCount = analytics.distribution.length;
  const rotateLabels = categoryCount > 6;
  const lineLength = rotateLabels ? 14 : categoryCount <= 3 ? 30 : categoryCount <= 5 ? 20 : 14;
  return {
    ...baseOptions('Répartition des réponses en colonnes'),
    grid: { left: 48, right: 24, top: 24, bottom: 12, containLabel: true },
    tooltip: {
      ...TOOLTIP_STYLE,
      trigger: 'axis' as const,
      axisPointer: { type: 'shadow' as const },
      formatter: (items: { dataIndex: number }[]) => answerTooltip(items[0].dataIndex)
    },
    xAxis: {
      type: 'category' as const,
      data: labels,
      triggerEvent: true,
      axisLabel: {
        ...AXIS_LABEL,
        rotate: rotateLabels ? 35 : 0,
        interval: 0,
        lineHeight: 14,
        formatter: (label: string) => shortLabel(label, lineLength, rotateLabels ? 1 : 2)
      },
      axisLine: AXIS_LINE,
      axisTick: { show: false }
    },
    yAxis: { type: 'value' as const, minInterval: 1, axisLabel: AXIS_LABEL, splitLine: SPLIT_LINE },
    series: [
      {
        type: 'bar' as const,
        data: coloredBars(analytics.distribution.map((item) => item.count)),
        barMaxWidth: 34,
        label: valueLabel('top', categoryCount <= 12)
      }
    ]
  };
}

/** Étiquette « NOM (x %) » des secteurs, colorée comme la part. */
export function pieLabel(): object {
  return {
    color: 'inherit',
    fontSize: 12,
    fontWeight: 500,
    formatter: (item: { name: string; percent: number }) =>
      `${shortLabel(item.name, 22, 2)} (${Math.round(item.percent).toLocaleString('fr-FR')} %)`
  };
}

/** Valeur affichée au bout de chaque barre. */
function valueLabel(position: 'top' | 'right', show = true): object {
  return {
    show,
    position,
    color: '#374151',
    fontSize: 11,
    fontWeight: 600,
    formatter: (item: { value: number }) => (item.value ? item.value.toLocaleString('fr-FR') : '')
  };
}

/** Tableau de bord : interviews par statut, une couleur par statut. */
export function statusOptions(dashboard: Dashboard): EChartsCoreOption {
  return {
    ...baseOptions('Nombre d’interviews par statut'),
    grid: { left: 16, right: 48, top: 12, bottom: 20, containLabel: true },
    tooltip: {
      ...TOOLTIP_STYLE,
      trigger: 'axis' as const,
      axisPointer: { type: 'shadow' as const },
      formatter: (items: { dataIndex: number; value: number }[]) => {
        const item = items[0];
        const status = dashboard.statuses[item.dataIndex];
        return `${statusLabel(status.status)}<br/><strong>${item.value.toLocaleString('fr-FR')}</strong> interview(s)`;
      }
    },
    xAxis: { type: 'value' as const, minInterval: 1, axisLabel: AXIS_LABEL, splitLine: SPLIT_LINE },
    yAxis: {
      type: 'category' as const,
      data: dashboard.statuses.map((item) => statusLabel(item.status)),
      triggerEvent: true,
      inverse: true,
      axisLabel: { ...AXIS_LABEL, color: '#374151', width: 150, overflow: 'truncate' as const },
      axisLine: { show: false },
      axisTick: { show: false }
    },
    series: [
      {
        type: 'bar' as const,
        data: coloredBars(dashboard.statuses.map((item) => item.count), true),
        barMaxWidth: 22,
        label: valueLabel('right')
      }
    ]
  };
}

/** Tableau de bord : part de chaque statut, en anneau. */
export function statusPieOptions(dashboard: Dashboard): EChartsCoreOption {
  return {
    ...baseOptions('Part des interviews par statut'),
    tooltip: {
      ...TOOLTIP_STYLE,
      trigger: 'item' as const,
      formatter: (item: { name: string; value: number; percent: number }) =>
        `${escapeHtml(item.name)}<br/><strong>${item.value.toLocaleString('fr-FR')}</strong> interview(s) · ${item.percent} %`
    },
    series: [
      {
        type: 'pie' as const,
        radius: ['42%', '68%'],
        center: ['50%', '52%'],
        data: dashboard.statuses.map((item) => ({ name: statusLabel(item.status), value: item.count })),
        label: pieLabel(),
        labelLine: { length: 10, length2: 8 },
        itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 4 }
      }
    ]
  };
}

export function trendOptions(dashboard: Dashboard): EChartsCoreOption {
  return {
    ...baseOptions('Interviews mises à jour par jour'),
    grid: { left: 42, right: 22, top: 20, bottom: 34 },
    tooltip: { ...TOOLTIP_STYLE, trigger: 'axis' as const },
    xAxis: {
      type: 'category' as const,
      boundaryGap: false,
      data: dashboard.updatesByDay.map((item) =>
        new Date(`${item.date}T00:00:00`).toLocaleDateString('fr-FR', {
          day: '2-digit',
          month: 'short',
          timeZone: 'UTC'
        })
      ),
      axisLabel: { ...AXIS_LABEL, hideOverlap: true },
      axisLine: AXIS_LINE,
      axisTick: { show: false }
    },
    yAxis: { type: 'value' as const, minInterval: 1, axisLabel: AXIS_LABEL, splitLine: SPLIT_LINE },
    series: [
      {
        type: 'line' as const,
        name: 'Interviews',
        data: dashboard.updatesByDay.map((item) => item.count),
        smooth: 0.28,
        symbolSize: 7,
        lineStyle: { color: '#10b981', width: 3 },
        itemStyle: { color: '#10b981', borderColor: '#ffffff', borderWidth: 2 },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(16, 185, 129, 0.28)' },
              { offset: 1, color: 'rgba(16, 185, 129, 0.02)' }
            ]
          }
        }
      }
    ]
  };
}
