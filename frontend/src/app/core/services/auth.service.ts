import { HttpClient, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Observable, catchError, map, of, shareReplay, switchMap, tap, throwError, timeout } from 'rxjs';

export interface AuthSession {
  authenticated: boolean;
  username: string | null;
  canReviewInterviews: boolean;
}

type AuthPhase = 'checking' | 'anonymous' | 'authenticated';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  readonly phase = signal<AuthPhase>('checking');
  readonly username = signal<string | null>(null);
  readonly connectionError = signal('');
  private sessionCheck$: Observable<AuthSession> | null = null;

  ensureSession(): Observable<AuthSession> {
    if (!this.sessionCheck$) {
      this.sessionCheck$ = this.http.get('/api/auth/csrf').pipe(
        switchMap(() => this.http.get<AuthSession>('/api/auth/session')),
        timeout({ first: 5000 }),
        tap((session) => this.applySession(session)),
        catchError(() => {
          this.username.set(null);
          this.phase.set('anonymous');
          this.connectionError.set('Impossible de contacter le serveur. Vérifiez votre connexion puis réessayez.');
          return of({ authenticated: false, username: null, canReviewInterviews: false });
        }),
        shareReplay(1)
      );
    }
    return this.sessionCheck$;
  }

  login(username: string, password: string): Observable<void> {
    this.connectionError.set('');
    return this.http.get('/api/auth/csrf').pipe(
      switchMap(() => this.http.post<AuthSession>('/api/auth/login', { username, password })),
      switchMap((session) => this.http.get('/api/auth/csrf').pipe(
        tap(() => {
          this.applySession(session);
          this.sessionCheck$ = of(session);
        })
      )),
      map(() => undefined),
      catchError((error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 503) {
          this.connectionError.set('Survey Solutions HQ est indisponible. Réessayez plus tard.');
        }
        return throwError(() => error);
      })
    );
  }

  logout(): Observable<void> {
    return this.http.get('/api/auth/csrf').pipe(
      switchMap(() => this.http.post<void>('/api/auth/logout', {})),
      tap(() => {
        this.username.set(null);
        this.phase.set('anonymous');
        this.sessionCheck$ = of({ authenticated: false, username: null, canReviewInterviews: false });
      })
    );
  }

  expireSession(): void {
    this.username.set(null);
    this.phase.set('anonymous');
    this.sessionCheck$ = of({ authenticated: false, username: null, canReviewInterviews: false });
  }

  private applySession(session: AuthSession): void {
    if (session.authenticated) {
      this.username.set(session.username);
      this.phase.set('authenticated');
      this.connectionError.set('');
    } else {
      this.username.set(null);
      this.phase.set('anonymous');
    }
  }
}

export const authExpiryInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith('/api/') || request.url.startsWith('/api/auth/')) {
    return next(request);
  }
  const auth = inject(AuthService);
  const router = inject(Router);
  return next(request).pipe(catchError((error: unknown) => {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      auth.expireSession();
      // Plusieurs requêtes peuvent échouer en même temps : une seule redirection, sans
      // imbriquer /login dans son propre returnUrl.
      if (!router.url.startsWith('/login')) {
        void router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
      }
    }
    return throwError(() => error);
  }));
};

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.ensureSession().pipe(
    map((session) => session.authenticated
      ? true
      : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } }))
  );
};

const RELOAD_MARKER = 'survey-analytics:chunk-reload';

/** Fichier JS introuvable : l'application a été redéployée depuis l'ouverture de l'onglet. */
export function isStaleBundleError(error: unknown): boolean {
  const text = error instanceof Error ? `${error.name} ${error.message}` : String(error ?? '');
  return /ChunkLoadError|Loading chunk|dynamically imported module|Importing a module script failed|MIME type/i.test(text);
}

/**
 * Gestionnaire d'erreur de navigation : après un redéploiement, recharge la page complète vers
 * la destination demandée pour récupérer la nouvelle version (une seule fois, pour éviter une boucle).
 */
export function reloadOnStaleBundle(
  error: unknown,
  targetUrl: string,
  storage: Storage | null = safeSession(),
  reload: (url: string) => void = (url) => window.location.assign(url)
): boolean {
  if (!isStaleBundleError(error)) {
    return false;
  }
  if (storage?.getItem(RELOAD_MARKER) === targetUrl) {
    storage.removeItem(RELOAD_MARKER);
    return false;
  }
  storage?.setItem(RELOAD_MARKER, targetUrl);
  reload(targetUrl);
  return true;
}

/** Navigation réussie : le rechargement éventuel a fonctionné, on oublie le marqueur. */
export function clearStaleBundleMarker(storage: Storage | null = safeSession()): void {
  storage?.removeItem(RELOAD_MARKER);
}

function safeSession(): Storage | null {
  try {
    return window.sessionStorage;
  } catch {
    return null;
  }
}
