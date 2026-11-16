import { ComponentFixture, TestBed } from '@angular/core/testing';
import { InfoTooltipComponent } from './info-tooltip';

describe('InfoTooltipComponent', () => {
  let fixture: ComponentFixture<InfoTooltipComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InfoTooltipComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(InfoTooltipComponent);
    fixture.componentRef.setInput('text', 'Réponses ÷ total × 100.');
    fixture.detectChanges();
  });

  it('affiche la formule au survol puis la masque à la sortie', () => {
    const trigger = fixture.nativeElement.querySelector('.info-tooltip') as HTMLElement;

    trigger.dispatchEvent(new MouseEvent('mouseenter'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="tooltip"]')?.textContent)
      .toContain('Réponses ÷ total × 100.');

    trigger.dispatchEvent(new MouseEvent('mouseleave'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="tooltip"]')).toBeNull();
  });

  it('affiche la formule lorsque le bouton reçoit le focus clavier', () => {
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;

    button.dispatchEvent(new FocusEvent('focus'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="tooltip"]')?.textContent)
      .toContain('Réponses ÷ total × 100.');
    expect(button.getAttribute('aria-expanded')).toBe('true');
  });
});
