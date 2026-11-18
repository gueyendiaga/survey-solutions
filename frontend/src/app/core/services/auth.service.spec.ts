import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService, isStaleBundleError, reloadOnStaleBundle } from './auth.service';

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the existing HQ session before allowing access', () => {
    auth.ensureSession().subscribe();
    http.expectOne('/api/auth/csrf').flush({ token: 'csrf-token' });
    http.expectOne('/api/auth/session').flush({
      authenticated: true, username: 'hq-user', canReviewInterviews: true
    });

    expect(auth.phase()).toBe('authenticated');
    expect(auth.username()).toBe('hq-user');
  });

  it('initializes CSRF, authenticates and refreshes the CSRF token after login', () => {
    let completed = false;
    auth.login('hq-user', 'secret').subscribe(() => completed = true);
    http.expectOne('/api/auth/csrf').flush({ token: 'pre-login-token' });
    const login = http.expectOne('/api/auth/login');
    expect(login.request.method).toBe('POST');
    expect(login.request.body).toEqual({ username: 'hq-user', password: 'secret' });
    login.flush({ authenticated: true, username: 'hq-user', canReviewInterviews: true });
    http.expectOne('/api/auth/csrf').flush({ token: 'session-token' });

    expect(completed).toBeTrue();
    expect(auth.phase()).toBe('authenticated');
  });

  it('invalidates the server session before clearing the local auth state', () => {
    auth.ensureSession().subscribe();
    http.expectOne('/api/auth/csrf').flush({ token: 'csrf-token' });
    http.expectOne('/api/auth/session').flush({
      authenticated: true, username: 'hq-user', canReviewInterviews: true
    });
    auth.logout().subscribe();
    http.expectOne('/api/auth/csrf').flush({ token: 'csrf-token' });
    http.expectOne('/api/auth/logout').flush(null);

    expect(auth.phase()).toBe('anonymous');
    expect(auth.username()).toBeNull();
  });

  it('recharge une seule fois la page quand un fichier JS a disparu après un redéploiement', () => {
    const values = new Map<string, string>();
    const storage = {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => void values.set(key, value),
      removeItem: (key: string) => void values.delete(key)
    } as Storage;
    const reloads: string[] = [];
    const stale = new TypeError('Failed to fetch dynamically imported module: /chunk-OLD.js');

    expect(isStaleBundleError(stale)).toBeTrue();
    expect(isStaleBundleError(new Error('Http failure response: 500'))).toBeFalse();
    expect(reloadOnStaleBundle(stale, '/questionnaires/q/1/qualite', storage, (url) => reloads.push(url))).toBeTrue();
    // Si l'erreur persiste après rechargement, on n'insiste pas (pas de boucle).
    expect(reloadOnStaleBundle(stale, '/questionnaires/q/1/qualite', storage, (url) => reloads.push(url))).toBeFalse();
    expect(reloads).toEqual(['/questionnaires/q/1/qualite']);
  });
});
