import { Routes } from '@angular/router';
import { DashboardPageComponent } from './pages/dashboard-page/dashboard-page';
import { QuestionnairesPageComponent } from './pages/questionnaires-page/questionnaires-page';
import { DEFAULT_TAB_SLUG } from './questionnaire/questionnaire-tabs';
import { authGuard } from './core/services/auth.service';
import { LoginPageComponent } from './shared/components/login-page/login-page';

export const routes: Routes = [
  { path: 'login', component: LoginPageComponent, title: 'Connexion · Survey Analytics' },
  { path: '', component: DashboardPageComponent, canActivate: [authGuard], title: 'Tableau de bord · Survey Analytics' },
  { path: 'questionnaires', component: QuestionnairesPageComponent, canActivate: [authGuard], title: 'Questionnaires · Survey Analytics' },
  { path: 'questionnaires/:questionnaireId/:version', redirectTo: `questionnaires/:questionnaireId/:version/${DEFAULT_TAB_SLUG}`, pathMatch: 'full' },
  {
    // Page d'un questionnaire, chargée à la demande : :tab = suivi-terrain | variables | qualite | rapports.
    path: 'questionnaires/:questionnaireId/:version/:tab',
    canActivate: [authGuard],
    loadComponent: () => import('./questionnaire/questionnaire-page/questionnaire-page').then((module) => module.QuestionnairePageComponent),
    title: 'Questionnaire · Survey Analytics'
  },
  { path: '**', redirectTo: '' }
];
