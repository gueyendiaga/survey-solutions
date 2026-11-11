import {NgOptimizedImage, registerLocaleData} from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Router } from '@angular/router';
import { CatalogService } from './core/services/catalog.service';
import { AuthService } from './core/services/auth.service';

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
