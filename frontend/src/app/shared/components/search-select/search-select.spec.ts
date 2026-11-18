import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SearchSelectComponent } from './search-select';

describe('SearchSelectComponent', () => {
  let fixture: ComponentFixture<SearchSelectComponent>;
  let element: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [SearchSelectComponent] }).compileComponents();
    fixture = TestBed.createComponent(SearchSelectComponent);
    fixture.componentInstance.options = [
      { value: 's01q04', label: 'Quel âge avait <font color="blue">le chef</font> ? (s01q04)' },
      { value: 'x', label: 'Piège<img src="x" onerror="window.selectPwned = true">' }
    ];
    fixture.componentInstance.writeValue('s01q04');
    fixture.detectChanges();
    element = fixture.nativeElement as HTMLElement;
  });

  it('affiche le HTML des libellés HQ en l’assainissant', () => {
    const selected = element.querySelector('.selected-label')!;
    expect(selected.textContent).toBe('Quel âge avait le chef ? (s01q04)');
    expect(selected.querySelector('font')?.getAttribute('color')).toBe('blue');

    element.querySelector<HTMLButtonElement>('.select-trigger')!.click();
    fixture.detectChanges();
    const trap = element.querySelectorAll('.option')[1];
    expect(trap.querySelector('img')?.hasAttribute('onerror') ?? false).toBeFalse();
    expect((window as unknown as { selectPwned?: boolean }).selectPwned).toBeUndefined();
  });

  it('recherche dans le texte, pas dans les balises', () => {
    element.querySelector<HTMLButtonElement>('.select-trigger')!.click();
    fixture.detectChanges();
    const search = element.querySelector<HTMLInputElement>('input[type="search"]')!;
    search.value = 'font';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(element.querySelectorAll('.option').length).toBe(0);

    search.value = 'chef';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(element.querySelectorAll('.option').length).toBe(1);
  });
});
