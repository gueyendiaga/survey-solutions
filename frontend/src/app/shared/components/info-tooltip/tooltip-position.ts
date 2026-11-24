export type TooltipSide = 'right' | 'left' | 'above' | 'below';

export interface TooltipPosition {
  side: TooltipSide;
  left: number;
  top: number;
  /** Position de la flèche le long du bord de la bulle qui fait face à l’icône. */
  arrow: number;
}

interface Size {
  width: number;
  height: number;
}

type Anchor = Pick<DOMRect, 'left' | 'right' | 'top' | 'bottom' | 'width' | 'height'>;

/** Distance entre l’icône et la bulle. */
const GAP = 8;
/** Marge minimale avec le bord de la fenêtre. */
const MARGIN = 12;
/** Garde la flèche hors des coins arrondis. */
const ARROW_INSET = 12;

/** Place la bulle contre l’icône : à droite si possible, sinon à gauche, au-dessus ou au-dessous. */
export function tooltipPosition(anchor: Anchor, bubble: Size, viewport: Size): TooltipPosition {
  const side = chooseSide(anchor, bubble, viewport);
  const centerX = anchor.left + anchor.width / 2;
  const centerY = anchor.top + anchor.height / 2;

  if (side === 'right' || side === 'left') {
    const left = side === 'right' ? anchor.right + GAP : anchor.left - GAP - bubble.width;
    const top = clamp(centerY - bubble.height / 2, MARGIN, viewport.height - bubble.height - MARGIN);
    return { side, left, top, arrow: clamp(centerY - top, ARROW_INSET, bubble.height - ARROW_INSET) };
  }

  const left = clamp(centerX - bubble.width / 2, MARGIN, viewport.width - bubble.width - MARGIN);
  const top = side === 'above' ? anchor.top - GAP - bubble.height : anchor.bottom + GAP;
  return { side, left, top, arrow: clamp(centerX - left, ARROW_INSET, bubble.width - ARROW_INSET) };
}

function chooseSide(anchor: Anchor, bubble: Size, viewport: Size): TooltipSide {
  if (anchor.right + GAP + bubble.width + MARGIN <= viewport.width) return 'right';
  if (anchor.left - GAP - bubble.width >= MARGIN) return 'left';
  return anchor.top - GAP - bubble.height >= MARGIN ? 'above' : 'below';
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(value, max));
}
