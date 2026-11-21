import { matchRegions, regionFromLabel } from './senegal-regions';

describe('senegal-regions', () => {
  it('recognises region labels whatever their spelling', () => {
    expect(regionFromLabel('DAKAR')?.name).toBe('Dakar');
    expect(regionFromLabel('Région de Thiès')?.name).toBe('Thiès');
    expect(regionFromLabel('04 - Saint Louis')?.name).toBe('Saint-Louis');
    expect(regionFromLabel('SEDHIOU')?.name).toBe('Sédhiou');
    expect(regionFromLabel('Urbain')).toBeNull();
  });

  it('falls back to ANSD codes only for a region-like variable', () => {
    const rows = [{ label: 'Modalité 1', code: '1' }, { label: 'Modalité 7', code: '07' }];
    expect(matchRegions(rows, (row) => row.label, (row) => row.code, 'region').map((m) => m.region.name))
      .toEqual(['Dakar', 'Thiès']);
    expect(matchRegions(rows, (row) => row.label, (row) => row.code, 'milieu')).toEqual([]);
  });
});
