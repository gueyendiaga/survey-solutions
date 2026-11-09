import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { CatalogService } from './core/services/catalog.service';

registerLocaleData(localeFr);

/**
 * Coquille de l'application : menu latéral et zone de contenu routée. Toute la logique métier
 * vit dans les pages (tableau de bord, questionnaires, questionnaire ouvert).
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly catalog = inject(CatalogService);
}
