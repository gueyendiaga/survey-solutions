import { normalizeText } from './table-state';

/** Région du Sénégal : code ANSD (01-14) et nom officiel, identique à la propriété `name` du GeoJSON. */
export interface SenegalRegion {
  code: string;
  name: string;
}

/** Les 14 régions, dans l'ordre des codes ANSD. */
export const SENEGAL_REGIONS: SenegalRegion[] = [
  { code: '01', name: 'Dakar' },
  { code: '02', name: 'Ziguinchor' },
  { code: '03', name: 'Diourbel' },
  { code: '04', name: 'Saint-Louis' },
  { code: '05', name: 'Tambacounda' },
  { code: '06', name: 'Kaolack' },
  { code: '07', name: 'Thiès' },
  { code: '08', name: 'Louga' },
  { code: '09', name: 'Fatick' },
  { code: '10', name: 'Kolda' },
  { code: '11', name: 'Matam' },
  { code: '12', name: 'Kaffrine' },
  { code: '13', name: 'Kédougou' },
  { code: '14', name: 'Sédhiou' }
];

/** Graphies rencontrées dans les questionnaires, après normalisation (voir regionKey). */
const ALIASES: Record<string, string> = {
  'st louis': 'Saint-Louis',
  'saint louis': 'Saint-Louis',
  'sedhiou': 'Sédhiou',
  'sedioh': 'Sédhiou',
  'kedougou': 'Kédougou',
  'thies': 'Thiès',
  'tamba': 'Tambacounda',
  'ziguinchor': 'Ziguinchor',
  'kaffrine': 'Kaffrine'
};

/** Clé de comparaison : sans accents, minuscules, ponctuation et préfixe « région de » retirés. */
export function regionKey(label: string): string {
  return normalizeText(label.replace(/<[^>]*>/g, ' '))
    .replace(/[^a-z0-9]+/g, ' ')
    .replace(/^(region|reg)( de| du)? /, '')
    .replace(/^\d+\s*[-.]?\s+/, '')
    .trim();
}

const BY_KEY = new Map<string, SenegalRegion>(SENEGAL_REGIONS.map((region) => [regionKey(region.name), region]));

/** Région correspondant à un libellé de modalité (« DAKAR », « Région de Thiès », « 04 - Saint Louis »…). */
export function regionFromLabel(label: string | null | undefined): SenegalRegion | null {
  if (!label) {
    return null;
  }
  const key = regionKey(label);
  const alias = ALIASES[key];
  if (alias) {
    return SENEGAL_REGIONS.find((region) => region.name === alias) ?? null;
  }
  return BY_KEY.get(key) ?? null;
}

/** Région correspondant à un code ANSD (« 1 », « 01 ») ou ISO (« SN-DK »). */
export function regionFromCode(code: string | null | undefined): SenegalRegion | null {
  const value = (code ?? '').trim();
  if (/^\d{1,2}$/.test(value)) {
    return SENEGAL_REGIONS.find((region) => Number(region.code) === Number(value)) ?? null;
  }
  return null;
}

export interface RegionMatch<T> {
  region: SenegalRegion;
  row: T;
}

/**
 * Associe les modalités d'une variable aux régions. Les libellés priment ; les codes 1-14 ne sont
 * utilisés que si aucun libellé n'est reconnu et que la variable ressemble à une région
 * (nom contenant « reg »), pour ne pas cartographier par erreur une variable codée 1, 2, 3…
 */
export function matchRegions<T>(
  rows: T[],
  label: (row: T) => string,
  code: (row: T) => string | null,
  variable: string | null
): RegionMatch<T>[] {
  const byLabel = rows
    .map((row) => ({ row, region: regionFromLabel(label(row)) }))
    .filter((item): item is RegionMatch<T> => item.region !== null);
  if (byLabel.length) {
    return byLabel;
  }
  if (!variable || !/reg/i.test(variable)) {
    return [];
  }
  return rows
    .map((row) => ({ row, region: regionFromCode(code(row)) }))
    .filter((item): item is RegionMatch<T> => item.region !== null);
}
