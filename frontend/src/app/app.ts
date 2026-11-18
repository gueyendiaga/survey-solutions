import {NgOptimizedImage, registerLocaleData} from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map, tap } from 'rxjs';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { CatalogService } from './core/services/catalog.service';
import { AuthService, clearStaleBundleMarker } from './core/services/auth.service';

registerLocaleData(localeFr);

/**
 * Coquille de l'application : menu latéral et zone de contenu routée. Toute la logique métier
 * vit dans les pages (tableau de bord, questionnaires, questionnaire ouvert).
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NgOptimizedImage],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly catalog = inject(CatalogService);
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly onLoginPage = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      tap(() => clearStaleBundleMarker()),
      map((event) => isLoginUrl(event.urlAfterRedirects))
    ),
    { initialValue: isLoginUrl(this.router.url) }
  );
  /**
   * Le menu latéral n'apparaît que pour une session ouverte et hors de la page de connexion :
   * même si l'état de session et la route divergent un instant, la connexion reste plein écran.
   */
  protected readonly showShell = computed(() => this.auth.phase() === 'authenticated' && !this.onLoginPage());
  protected sidebarCollapsed = false;
  protected logoutError = false;

  protected logout(): void {
    this.logoutError = false;
    this.auth.logout().subscribe({
      next: () => void this.router.navigate(['/login']),
      error: () => this.logoutError = true
    });
  }

  protected toggleSidebar(): void {
    this.sidebarCollapsed = !this.sidebarCollapsed;
  }
}

function isLoginUrl(url: string): boolean {
  return url === '/login' || url.startsWith('/login?') || url.startsWith('/login#');
}
