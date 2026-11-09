import { Component, Input } from '@angular/core';

/** Carte d'attente : chargement en cours, ou import des données depuis HQ. */
@Component({
  selector: 'app-loading-state',
  standalone: true,
  templateUrl: './loading-state.html',
  styleUrl: './loading-state.css'
})
export class LoadingStateComponent {
  @Input() label = 'Chargement…';
  /** Vrai pendant une synchronisation HQ : le message explique l'attente. */
  @Input() importing = false;
}
