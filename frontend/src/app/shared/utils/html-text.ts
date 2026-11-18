/**
 * Libellés HQ (questions, modalités) : le Designer Survey Solutions autorise un HTML léger
 * (<b>, <i>, <font color>, <br>…).
 *
 * - Dans un template, on les affiche avec [innerHTML] : Angular assainit le contenu (scripts,
 *   gestionnaires d'événements et URL dangereuses retirés). Ne jamais contourner cet
 *   assainissement (bypassSecurityTrust…).
 * - Hors du DOM (recherche, graphiques, info-bulles, attributs), on utilise le texte brut.
 */

let parser: DOMParser | null = null;

/** Texte lisible d'un libellé HTML : balises retirées, entités décodées, espaces normalisés. */
export function htmlToText(value: string | null | undefined): string {
  if (value === null || value === undefined || value === '') {
    return '';
  }
  const text = String(value);
  if (!/[<&]/.test(text)) {
    return text.replace(/\s+/g, ' ').trim();
  }
  // DOMParser produit un document inerte : aucun script exécuté, aucune ressource chargée.
  parser ??= new DOMParser();
  const document = parser.parseFromString(text.replace(/<br\s*\/?>/gi, ' '), 'text/html');
  document.querySelectorAll('script, style, template, noscript').forEach((node) => node.remove());
  return (document.body.textContent ?? '').replace(/\s+/g, ' ').trim();
}

/** Échappe un texte pour l'insérer dans une chaîne HTML construite à la main (info-bulles ECharts). */
export function escapeHtml(value: string | null | undefined): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
