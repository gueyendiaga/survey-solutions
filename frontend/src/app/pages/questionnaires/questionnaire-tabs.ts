/** Onglets d'un questionnaire ouvert. */
export type QuestionnaireTab = 'fieldwork' | 'review' | 'variables' | 'quality' | 'reports';

export interface TabDefinition {
  id: QuestionnaireTab;
  label: string;
  /** Segment d'URL : /questionnaires/:id/:version/:slug */
  slug: string;
}

export const QUESTIONNAIRE_TABS: TabDefinition[] = [
  { id: 'fieldwork', label: 'Suivi terrain', slug: 'suivi-terrain' },
  // { id: 'review', label: 'Revue', slug: 'revue' },
  { id: 'variables', label: 'Variables', slug: 'variables' },
  { id: 'quality', label: 'Qualité des données', slug: 'qualite' },
  { id: 'reports', label: 'Rapports', slug: 'rapports' }
];

export const DEFAULT_TAB_SLUG = QUESTIONNAIRE_TABS[0].slug;

export function tabFromSlug(slug: string | null | undefined): QuestionnaireTab | null {
  return QUESTIONNAIRE_TABS.find((tab) => tab.slug === slug)?.id ?? null;
}

export function slugFor(tab: QuestionnaireTab): string {
  return QUESTIONNAIRE_TABS.find((item) => item.id === tab)?.slug ?? DEFAULT_TAB_SLUG;
}

/** Badges affichés à côté des onglets (null : pas de badge). */
export interface TabBadges {
  review: number | null;
  variables: number | null;
  quality: number | null;
  reports: number | null;
}
