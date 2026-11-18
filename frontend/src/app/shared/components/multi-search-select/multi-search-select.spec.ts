import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MultiSearchSelectComponent } from './multi-search-select';

describe('MultiSearchSelectComponent', () => {
  let fixture: ComponentFixture<MultiSearchSelectComponent>;
  let element: HTMLElement;
  let changes: string[][];

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [MultiSearchSelectComponent] }).compileComponents();
    fixture = TestBed.createComponent(MultiSearchSelectComponent);
    const component = fixture.componentInstance;
    component.options = [
      { value: 's00q01', label: 'Région (s00q01)' },
      { value: 's00q02', label: 'Département (s00q02)' },
      { value: 'B11', label: 'B11 <b>statut</b> d’occupation (B11)' }
    ];
    component.itemsLabel = 'question(s)';
    changes = [];
    component.registerOnChange((value) => changes.push(value));
    component.writeValue(['B11']);
    fixture.detectChanges();
    element = fixture.nativeElement as HTMLElement;
  });

  function open(): void {
    element.querySelector<HTMLButtonElement>('.ms-trigger')!.click();
    fixture.detectChanges();
  }

  it('coche plusieurs questions sans refermer la liste et affiche des puces retirables', () => {
    expect(element.querySelector('.ms-trigger')?.textContent).toContain('1 question(s) sélectionné');
    expect(element.querySelector('.ms-chip b')?.textContent).toBe('statut');

    open();
    element.querySelectorAll<HTMLButtonElement>('.ms-option')[0].click();
    fixture.detectChanges();
    expect(element.querySelector('.ms-menu')).not.toBeNull();
    expect(changes.at(-1)).toEqual(['B11', 's00q01']);

    element.querySelector<HTMLButtonElement>('.ms-chip button')!.click();
    fixture.detectChanges();
    expect(changes.at(-1)).toEqual(['s00q01']);
  });

  it('coche tous les résultats d’une recherche puis efface tout', () => {
    open();
    const search = element.querySelector<HTMLInputElement>('input[type="search"]')!;
    search.value = 'dép';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(element.querySelectorAll('.ms-option').length).toBe(1);

    [...element.querySelectorAll<HTMLButtonElement>('.ms-tools button')].find((b) => b.textContent?.includes('Cocher'))!.click();
    expect(changes.at(-1)).toEqual(['B11', 's00q02']);

    [...element.querySelectorAll<HTMLButtonElement>('.ms-tools button')].find((b) => b.textContent?.includes('effacer'))!.click();
    expect(changes.at(-1)).toEqual([]);
  });
});
