import { shortLabel } from './chart-options';

describe('shortLabel', () => {
  it('wraps long option labels on two lines and truncates the rest', () => {
    const label = 'YES, I consent to the sharing of my anonymized survey responses with third parties';
    const short = shortLabel(label, 30, 2);
    const lines = short.split('\n');

    expect(lines.length).toBe(2);
    expect(lines.every((line) => line.length <= 30)).toBeTrue();
    expect(short.endsWith('…')).toBeTrue();
    expect(shortLabel('Oui', 30, 2)).toBe('Oui');
  });
});
