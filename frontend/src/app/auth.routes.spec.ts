import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';

describe('Routes protégées', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()]
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('redirige un visiteur anonyme vers /login en conservant la destination demandée', async () => {
    const harness = await RouterTestingHarness.create();
    const navigation = harness.navigateByUrl('/questionnaires');
    await new Promise((resolve) => setTimeout(resolve, 0));
    http.expectOne('/api/auth/csrf').flush({ token: 'csrf-token' });
    http.expectOne('/api/auth/session').flush({
      authenticated: false, username: null, canReviewInterviews: false
    });
    await navigation;
    harness.detectChanges();

    expect(TestBed.inject(Router).url).toBe('/login?returnUrl=%2Fquestionnaires');
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toContain('Connexion');
  });
});
