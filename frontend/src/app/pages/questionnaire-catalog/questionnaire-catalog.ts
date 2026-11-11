import { DatePipe } from '@angular/common';
import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Questionnaire } from '../../core/models/dashboard.models';

/** Tableau des questionnaires HQ ; « Ouvrir » mène à la page du questionnaire. */
@Component({
  selector: 'app-questionnaire-catalog',
  standalone: true,
  imports: [DatePipe, RouterLink],
  templateUrl: './questionnaire-catalog.html',
  styleUrl: './questionnaire-catalog.css'
})
export class QuestionnaireCatalogComponent {
  @Input({ required: true }) questionnaires: Questionnaire[] = [];
}
