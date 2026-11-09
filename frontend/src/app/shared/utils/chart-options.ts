import type { EChartsCoreOption } from 'echarts/core';
import { Dashboard, QuestionAnalytics } from '../../core/models/dashboard.models';
import { statusLabel } from './labels';

export type AnswerChartType = 'bar' | 'horizontalBar' | 'pie';

/** Palette des graphiques : couleur primaire, sa déclinaison claire, puis l'ocre. */
export const CHART_COLORS = ['#1e3398', '#5b6bc0', '#c27c1e', '#9aa5dc', '#16267a', '#e0a85a', '#1f6b45', '#8a4f0c'];

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
  if (type === 'pie') {
    return {
      animation: false,
      textStyle: { fontFamily: 'Inter, sans-serif' },
      tooltip: {
        trigger: 'item' as const,
        formatter: (item: { name: string; value: number; percent: number }) =>
          `${item.name}<br/><strong>${item.value.toLocaleString('fr-FR')}</strong> réponse(s) · ${item.percent}%`
      },
      series: [
        {
          type: 'pie' as const,
          color: CHART_COLORS,
          radius: '68%',
          data: analytics.distribution.map((item) => ({
            name: item.label,
            value: item.count
          })),
          label: {
            color: '#374151',
            formatter: '{b}\n{d}%'
          },
          itemStyle: { borderColor: '#fff', borderWidth: 2 }
        }
      ]
    };
  }

  if (type === 'horizontalBar') {
    return {
      animation: false,
      textStyle: { fontFamily: 'Inter, sans-serif' },
      grid: { left: 16, right: 40, top: 12, bottom: 24, containLabel: true },
      tooltip: {
        trigger: 'axis' as const,
        axisPointer: { type: 'shadow' as const },
        formatter: (items: { dataIndex: number; value: number }[]) => {
          const answer = analytics.distribution[items[0].dataIndex];
          return `${answer.label}<br/><strong>${answer.count.toLocaleString('fr-FR')}</strong> réponse(s) · ${answer.percentage}%`;
        }
      },
      xAxis: {
        type: 'value' as const,
        minInterval: 1,
        axisLabel: { color: '#6b7280' },
        splitLine: { lineStyle: { color: '#f0f2f6' } }
      },
      yAxis: {
        type: 'category' as const,
        data: analytics.distribution.map((item) => item.label),
        axisLabel: { color: '#374151', width: 160, overflow: 'truncate' as const },
        inverse: true,
        axisLine: { lineStyle: { color: '#e6eaf0' } },
        axisTick: { show: false }
      },
      series: [
        {
          type: 'bar' as const,
          data: analytics.distribution.map((item) => item.count),
          barMaxWidth: 40,
          itemStyle: { color: '#1e3398', borderRadius: [0, 6, 6, 0] }
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
    animation: false,
    textStyle: { fontFamily: 'Inter, sans-serif' },
    grid: { left: 48, right: 24, top: 20, bottom: 12, containLabel: true },
    tooltip: {
      trigger: 'axis' as const,
      axisPointer: { type: 'shadow' as const },
      formatter: (items: { dataIndex: number; value: number }[]) => {
        const answer = analytics.distribution[items[0].dataIndex];
        return `${answer.label}<br/><strong>${answer.count.toLocaleString('fr-FR')}</strong> réponse(s) · ${answer.percentage}%`;
      }
    },
    xAxis: {
      type: 'category' as const,
      data: analytics.distribution.map((item) => item.label),
      axisLabel: {
        color: '#6b7280',
        rotate: rotateLabels ? 30 : 0,
        interval: 0,
        lineHeight: 14,
        formatter: (label: string) => shortLabel(label, lineLength, rotateLabels ? 1 : 2)
      },
      axisLine: { lineStyle: { color: '#e6eaf0' } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value' as const,
      minInterval: 1,
      axisLabel: { color: '#6b7280' },
      splitLine: { lineStyle: { color: '#f0f2f6' } }
    },
    series: [
      {
        type: 'bar' as const,
        data: analytics.distribution.map((item) => item.count),
        barMaxWidth: 40,
        itemStyle: { color: '#1e3398', borderRadius: [6, 6, 0, 0] }
      }
    ]
  };
}

/** Tableau de bord : interviews par statut. */
export function statusOptions(dashboard: Dashboard): EChartsCoreOption {
  return {
    animation: false,
    textStyle: { fontFamily: 'Inter, sans-serif' },
    grid: { left: 190, right: 24, top: 16, bottom: 24 },
    tooltip: {
      trigger: 'axis' as const,
      axisPointer: { type: 'shadow' as const },
      formatter: (items: { dataIndex: number; value: number }[]) => {
        const item = items[0];
        const status = dashboard.statuses[item.dataIndex];
        return `${statusLabel(status.status)}<br/><strong>${item.value.toLocaleString('fr-FR')}</strong> interview(s)`;
      }
    },
    xAxis: {
      type: 'value' as const,
      minInterval: 1,
      axisLabel: { color: '#6b7280' },
      splitLine: { lineStyle: { color: '#f0f2f6' } }
    },
    yAxis: {
      type: 'category' as const,
      data: dashboard.statuses.map((item) => statusLabel(item.status)),
      axisLabel: { color: '#374151', width: 170, overflow: 'truncate' as const },
      axisLine: { show: false },
      axisTick: { show: false }
    },
    series: [
      {
        type: 'bar' as const,
        data: dashboard.statuses.map((item) => item.count),
        barMaxWidth: 18,
        itemStyle: { color: '#1e3398', borderRadius: [0, 5, 5, 0] },
        emphasis: { itemStyle: { color: '#16267a' } }
      }
    ]
  };
}

export function trendOptions(dashboard: Dashboard): EChartsCoreOption {
  return {
    animation: false,
    textStyle: { fontFamily: 'Inter, sans-serif' },
    grid: { left: 42, right: 22, top: 20, bottom: 34 },
    tooltip: { trigger: 'axis' as const },
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
      axisLabel: { color: '#6b7280', hideOverlap: true },
      axisLine: { lineStyle: { color: '#e6eaf0' } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value' as const,
      minInterval: 1,
      axisLabel: { color: '#6b7280' },
      splitLine: { lineStyle: { color: '#f0f2f6' } }
    },
    series: [
      {
        type: 'line' as const,
        data: dashboard.updatesByDay.map((item) => item.count),
        smooth: 0.28,
        symbolSize: 7,
        lineStyle: { color: '#1e3398', width: 3 },
        itemStyle: { color: '#1e3398', borderColor: '#ffffff', borderWidth: 2 },
        areaStyle: { color: 'rgba(30, 51, 152, 0.12)' }
      }
    ]
  };
}
