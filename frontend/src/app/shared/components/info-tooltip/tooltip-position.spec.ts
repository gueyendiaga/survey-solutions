import { tooltipPosition } from './tooltip-position';

const VIEWPORT = { width: 1000, height: 600 };
const BUBBLE = { width: 300, height: 80 };
const icon = (left: number, top: number) =>
  ({ left, top, right: left + 17, bottom: top + 17, width: 17, height: 17 });

describe('tooltipPosition', () => {
  it('colle la bulle à droite de l’icône, centrée verticalement', () => {
    expect(tooltipPosition(icon(200, 300), BUBBLE, VIEWPORT))
      .toEqual({ side: 'right', left: 225, top: 268.5, arrow: 40 });
  });

  it('passe à gauche quand la droite déborde', () => {
    const position = tooltipPosition(icon(900, 300), BUBBLE, VIEWPORT);
    expect(position.side).toBe('left');
    expect(position.left + BUBBLE.width).toBe(892);
  });

  it('passe au-dessus, ou au-dessous en haut de page, sur un écran étroit', () => {
    const narrow = { width: 375, height: 700 };
    const above = tooltipPosition(icon(180, 300), BUBBLE, narrow);
    expect(above.side).toBe('above');
    expect(above.top + BUBBLE.height).toBe(292);

    const below = tooltipPosition(icon(180, 20), BUBBLE, narrow);
    expect(below.side).toBe('below');
    expect(below.top).toBe(45);
  });

  it('reste dans la fenêtre et garde la flèche en face de l’icône', () => {
    const position = tooltipPosition(icon(200, 5), BUBBLE, VIEWPORT);
    expect(position.top).toBe(12);
    expect(position.arrow).toBe(12);
  });
});
