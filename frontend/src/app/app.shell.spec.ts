import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { App } from './app';
import { routes } from './app.routes';
import { AuthService, authExpiryInterceptor } from './core/services/auth.service';
import { CatalogService } from './core/services/catalog.service';
import { dashboardResponse } from './testing/fixtures';

describe('Coquille de l’application et expiration de session', () => {
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes),
        provideHttpClient(withInterceptors([authExpiryInterceptor])),
        provideHttpClientTesting()
      ]
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => http.verify());

  async function openDashboardAuthenticated() {
    const fixture = TestBed.createComponent(App);
    const navigation = router.navigateByUrl('/');
    await new Promise((resolve) => setTimeout(resolve, 0));
    http.expectOne('/api/auth/csrf').flush({ token: 'csrf' });
    http.expectOne('/api/auth/session').flush({ authenticated: true, username: 'gueye28', canReviewInterviews: true });
    await navigation;
    fixture.detectChanges();
    return fixture;
  }

  function sidebar(element: HTMLElement): HTMLElement | null {
    return element.querySelector('nav.sidebar');
  }

  it('masque le menu latéral et vide la session quand une requête renvoie 401', async () => {
    const fixture = await openDashboardAuthenticated();
    const element = fixture.nativeElement as HTMLElement;
    http.expectOne('/api/dashboard').flush(dashboardResponse());
    fixture.detectChanges();
    expect(sidebar(element)?.textContent).toContain('gueye28');

    // Session perdue côté serveur (redémarrage du backend) : la requête suivante renvoie 401.
    await router.navigateByUrl('/questionnaires');
    TestBed.inject(CatalogService).refresh();
    http.expectOne('/api/dashboard').flush(null, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(router.url).toBe('/login?returnUrl=%2Fquestionnaires');
    expect(TestBed.inject(AuthService).username()).toBeNull();
    expect(sidebar(element)).toBeNull();
    expect(element.textContent).not.toContain('HQ connecté');
    expect(element.querySelector('h1')?.textContent).toContain('Connexion');
  });

  it('affiche la connexion en plein écran même si l’état de session est encore « connecté »', async () => {
    const fixture = await openDashboardAuthenticated();
    http.expectOne('/api/dashboard').flush(dashboardResponse());
    const auth = TestBed.inject(AuthService);
    spyOn(auth, 'ensureSession').and.returnValue(of({ authenticated: false, username: null, canReviewInterviews: false }));

    await router.navigateByUrl('/login?returnUrl=%2Fquestionnaires');
    fixture.detectChanges();

    expect(auth.phase()).toBe('authenticated');
    expect(sidebar(fixture.nativeElement as HTMLElement)).toBeNull();
  });
});
