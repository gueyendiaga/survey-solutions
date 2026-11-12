import { Routes } from '@angular/router';
import { DashboardPageComponent } from './pages/dashboard-page/dashboard-page';
import { QuestionnaireCatalogPageComponent } from './pages/questionnaires/catalog-page/questionnaire-catalog-page';
import { DEFAULT_TAB_SLUG } from './pages/questionnaires/questionnaire-tabs';
import { authGuard } from './core/services/auth.service';
import { LoginPageComponent } from './pages/login-page/login-page';

export const routes: Routes = [
  { path: 'login', component: LoginPageComponent, title: 'Connexion · Survey Analytics' },
  { path: '', component: DashboardPageComponent, canActivate: [authGuard], title: 'Tableau de bord · Survey Analytics' },
  { path: 'questionnaires', component: QuestionnaireCatalogPageComponent, canActivate: [authGuard], title: 'Questionnaires · Survey Analytics' },
  { path: 'questionnaires/:questionnaireId/:version', redirectTo: `questionnaires/:questionnaireId/:version/${DEFAULT_TAB_SLUG}`, pathMatch: 'full' },
  {
    // Page d'un questionnaire, chargée à la demande : :tab = suivi-terrain | variables | qualite | rapports.
    path: 'questionnaires/:questionnaireId/:version/:tab',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/questionnaires/detail-page/questionnaire-detail-page').then((module) => module.QuestionnaireDetailPageComponent),
    title: 'Questionnaire · Survey Analytics'
  },
  { path: '**', redirectTo: '' }
];
