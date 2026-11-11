import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';

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
});
