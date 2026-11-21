import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, Output, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  FieldOperations,
  InterviewerTablet,
  QueueStage,
  TabletState,
  ZoneProgress
} from '../../../../core/models/dashboard.models';
import { InfoTooltipComponent } from '../../../../shared/components/info-tooltip/info-tooltip';
import { KpiCardComponent } from '../../../../shared/components/kpi-card/kpi-card';
import { RegionMapComponent, RegionValue } from '../../../../shared/components/region-map/region-map';
import { TablePagerComponent } from '../../../../shared/components/table-pager/table-pager';
import { TableSearchComponent } from '../../../../shared/components/table-search/table-search';
import { chartColor } from '../../../../shared/utils/chart-options';
import { htmlToText } from '../../../../shared/utils/html-text';
import { matchRegions } from '../../../../shared/utils/senegal-regions';
import { createTableState } from '../../../../shared/utils/table-state';
import {
  SearchSelectComponent,
  SearchSelectOption
} from '../../../../shared/components/search-select/search-select';
import { statusLabel } from '../../../../shared/utils/labels';

interface StateView {
  label: string;
  tone: 'ok' | 'warn' | 'danger' | 'muted' | 'blue';
}

const STATES: Record<TabletState, StateView> = {
  LOCKED: { label: 'Compte verrouillé', tone: 'danger' },
  NEVER: { label: 'Jamais actif', tone: 'danger' },
  INACTIVE: { label: 'Inactif', tone: 'warn' },
  ACTIVE: { label: 'Actif', tone: 'ok' }
};

/** Ordre d'affichage : les situations à traiter d'abord. */
const STATE_ORDER: TabletState[] = ['LOCKED', 'NEVER', 'INACTIVE', 'ACTIVE'];

interface Bucket {
  key: keyof Omit<QueueStage, 'total'>;
  label: string;
  tone: 'b1' | 'b2' | 'b3' | 'b4';
}

type MapMetric = 'interviews' | 'completed' | 'approvalRate';

const MAP_METRIC_LABELS: Record<MapMetric, string> = {
  interviews: 'Interviews',
  completed: 'Interviews terminées',
  approvalRate: 'Part approuvée'
};

/**
 * Opérations de terrain : activité des tablettes, file de validation, rendez-vous, mode de
 * collecte et avancement par zone (métadonnées HQ hors export Tabular).
 */
@Component({
  selector: 'app-field-operations',
  standalone: true,
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    InfoTooltipComponent,
    KpiCardComponent,
    RegionMapComponent,
    SearchSelectComponent,
    TablePagerComponent,
    TableSearchComponent
  ],
  templateUrl: './field-operations.html',
  styleUrl: './field-operations.css'
})
export class FieldOperationsComponent implements OnChanges {
  @Input({ required: true }) operations!: FieldOperations;
  @Output() readonly zoneVariableChange = new EventEmitter<string | null>();

  /** Copie de l'entrée en signal, pour les calculs dérivés (tri, recherche, carte). */
  private readonly data = signal<FieldOperations | null>(null);
  private readonly sorted = computed(() => {
    const operations = this.data();
    return operations ? this.sortInterviewers(operations.interviewers) : [];
  });
  protected readonly tablets = createTableState(
    () => this.sorted(),
    (row) => `${row.interviewer} ${row.supervisor ?? ''}`
  );
  protected readonly mapMetric = signal<MapMetric>('interviews');
  /** Modalités reconnues comme régions du Sénégal (vide : pas de carte). */
  protected readonly regionMatches = computed(() => {
    const zones = this.data()?.zones;
    return zones ? matchRegions(zones.rows, (row) => htmlToText(row.label), (row) => row.code, zones.variable) : [];
  });
  protected readonly mapValues = computed<RegionValue[]>(() => {
    const metric = this.mapMetric();
    return this.regionMatches().map(({ region, row }) => ({
      name: region.name,
      value: row[metric],
      details: [
        `Interviews : ${row.interviews.toLocaleString('fr-FR')}`,
        `Terminées : ${row.completed.toLocaleString('fr-FR')} · approuvées : ${row.approved.toLocaleString('fr-FR')}`,
        `Rejetées : ${row.rejected.toLocaleString('fr-FR')} · en cours : ${row.inProgress.toLocaleString('fr-FR')}`
      ]
    }));
  });
  protected readonly mapMetricLabel = computed(() => MAP_METRIC_LABELS[this.mapMetric()]);
  protected readonly color = chartColor;

  ngOnChanges(): void {
    this.data.set(this.operations);
  }

  protected readonly statusLabel = statusLabel;
  protected readonly buckets: Bucket[] = [
    { key: 'lessThanOneDay', label: '< 1 jour', tone: 'b1' },
    { key: 'oneToThreeDays', label: '1–3 jours', tone: 'b2' },
    { key: 'threeToSevenDays', label: '3–7 jours', tone: 'b3' },
    { key: 'moreThanSevenDays', label: '> 7 jours', tone: 'b4' }
  ];

  protected readonly tabletHelp =
    'Dernier contact tablette : dernière réception d’une affectation ou d’une interview par la tablette. '
    + 'Activité : ce contact ou la dernière interview terminée, selon le plus récent.';
  protected readonly validationHelp =
    'Attente comptée depuis la dernière complétion (en attente du superviseur) ou la dernière approbation '
    + 'du superviseur (en attente de HQ). Délais médians calculés sur les interviews déjà approuvées.';
  protected readonly zoneHelp =
    'Variables à choix unique du niveau principal ; les questions d’identification sont proposées en premier. '
    + 'Part approuvée = approuvées ÷ interviews × 100.';

  protected state(row: InterviewerTablet): StateView {
    return STATES[row.state] ?? STATES.ACTIVE;
  }

  /** Enquêteurs, situations à traiter d'abord puis par ancienneté d'activité. */
  private sortInterviewers(interviewers: InterviewerTablet[]): InterviewerTablet[] {
    return [...interviewers].sort((a, b) =>
      STATE_ORDER.indexOf(a.state) - STATE_ORDER.indexOf(b.state)
      || (b.daysSinceActivity ?? -1) - (a.daysSinceActivity ?? -1)
      || b.pendingAssignments - a.pendingAssignments
      || a.interviewer.localeCompare(b.interviewer, 'fr'));
  }

  protected activeShare(): number | null {
    const tablets = this.operations.tablets;
    return tablets.interviewers ? (tablets.active * 100) / tablets.interviewers : null;
  }

  /** Couleur de la pastille de part approuvée. */
  protected rateTone(rate: number): string {
    return rate >= 80 ? 'green' : rate >= 50 ? 'blue' : 'orange';
  }

  protected waitingTotal(): number {
    const queue = this.operations.validation;
    return queue.supervisor.total + queue.headquarters.total;
  }

  protected overdueWaiting(): number {
    const queue = this.operations.validation;
    return queue.supervisor.threeToSevenDays + queue.supervisor.moreThanSevenDays
      + queue.headquarters.threeToSevenDays + queue.headquarters.moreThanSevenDays;
  }

  protected share(stage: QueueStage, bucket: Bucket): number {
    return stage.total ? (stage[bucket.key] * 100) / stage.total : 0;
  }

  protected hours(value: number | null): string {
    if (value === null) {
      return '—';
    }
    if (value < 24) {
      return `${value.toLocaleString('fr-FR', { maximumFractionDigits: 1 })} h`;
    }
    return `${(value / 24).toLocaleString('fr-FR', { maximumFractionDigits: 1 })} j`;
  }

  protected days(value: number | null): string {
    if (value === null) {
      return '—';
    }
    return value < 1 ? '< 1 j' : `${value.toLocaleString('fr-FR', { maximumFractionDigits: 1 })} j`;
  }

  protected modeLabel(mode: string): string {
    return mode === 'CAPI' ? 'Tablette (CAPI)' : mode === 'CAWI' ? 'Web (CAWI)' : 'Non précisé';
  }

  /** Le mode n'est utile que si la collecte mélange tablette et web. */
  protected showModes(): boolean {
    return this.operations.modes.some((item) => item.mode === 'CAWI');
  }

  protected modeShare(count: number): number {
    const total = this.operations.modes.reduce((sum, item) => sum + item.count, 0);
    return total ? (count * 100) / total : 0;
  }

  protected zoneMax(): number {
    return Math.max(1, ...this.operations.zones.rows.map((row) => row.interviews));
  }

  protected zoneShare(row: ZoneProgress): number {
    return (row.interviews * 100) / this.zoneMax();
  }

  /** Variables proposées à la ventilation, questions d'identification d'abord (ordre du backend). */
  protected zoneOptions(): SearchSelectOption[] {
    return this.operations.zones.candidates.map((option) => ({
      value: option.variable,
      label: `${option.label} (${option.variable})${option.identifying ? ' · identification' : ''}`
    }));
  }
}
