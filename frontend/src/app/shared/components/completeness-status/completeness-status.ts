import { Component, Input } from '@angular/core';
import { QuestionCompleteness } from '../../../core/models/dashboard.models';

/** Pastille de complétude d'une variable : non posée, à examiner ou complète. */
@Component({
  selector: 'app-completeness-status',
  standalone: true,
  templateUrl: './completeness-status.html',
  styleUrl: './completeness-status.css'
})
export class CompletenessStatusComponent {
  @Input({ required: true }) item!: QuestionCompleteness;

  protected get posed(): boolean {
    return this.item.answeredCount + this.item.missingCount > 0;
  }
}
