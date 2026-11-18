import { InterviewHistoryRecord } from '../../../core/models/dashboard.models';
import { buildHistory, historyCsv, visibleEntries } from './interview-history.utils';

function record(index: number, action: string, timestamp: string, parameters: Record<string, string | null> = {},
  originatorName = 'gueye27', originatorRole = 'Interviewer'): InterviewHistoryRecord {
  return { index, action, originatorName, originatorRole, parameters, timestamp, offset: '00:00:00' };
}

describe('interview history', () => {
  const records: InterviewHistoryRecord[] = [
    record(0, 'InterviewCreated', '2026-11-13T11:49:15Z', { assignment: '14' }),
    record(1, 'Resumed', '2026-11-13T11:49:16Z', { deviceType: 'Tablet' }),
    record(2, 'AnswerSet', '2026-11-13T11:52:30Z', { question: 's00q07', answer: '8', roster: '' }),
    record(3, 'AnswerSet', '2026-11-13T11:52:57Z', { question: 's00q07', answer: '9', roster: '' }),
    record(4, 'AnswerSet', '2026-11-13T11:54:15Z', { question: 's01q02', answer: '1', roster: '1' }),
    record(5, 'AnswerRemoved', '2026-11-13T11:54:59Z', { question: 's01q02', roster: '1' }),
    record(6, 'Paused', '2026-11-13T12:09:16Z'),
    record(7, 'QuestionDeclaredValid', '2026-11-13T13:05:24Z', { question: 'B1', roster: '1' }, '', ''),
    record(8, 'VariableSet', '2026-11-13T13:05:24Z', { variable: 'Duree_enq', value: '71.7', roster: '' }, '', ''),
    record(9, 'Completed', '2026-11-13T13:05:24Z'),
    record(10, 'ReceivedBySupervisor', '2026-11-13T12:47:43Z', {}, '', ''),
    record(11, 'ApproveByHeadquarter', '2026-11-18T09:52:09Z', {}, 'gueye28', 'ApiUser')
  ];

  it('summarises sessions, answers, corrections and the latest decision', () => {
    const model = buildHistory(records);

    expect(model.summary.sessions).toBe(1);
    expect(model.summary.activeSeconds).toBe(20 * 60);
    expect(model.summary.answers).toBe(3);
    expect(model.summary.corrections).toBe(1);
    expect(model.summary.removals).toBe(1);
    expect(model.milestones.map((step) => step.label))
      .toEqual(['Créée', 'Première réponse', 'Terminée', 'Approuvée par HQ']);
    expect(model.counts).toEqual(jasmine.objectContaining({ workflow: 4, answer: 4, session: 2, system: 2, all: 12 }));
  });

  it('shows the previous value of corrected answers and readable roster rows', () => {
    const entries = buildHistory(records).entries;

    expect(entries[3]).toEqual(jasmine.objectContaining({ correction: true, previous: '8', value: '9', question: 's00q07' }));
    expect(entries[4].roster).toBe('ligne 1');
    expect(entries[11]).toEqual(jasmine.objectContaining({ actor: 'gueye28', role: 'Utilisateur API', tone: 'success' }));
    expect(entries[7].actor).toBe('Système');
  });

  it('filters by family, corrections and search, and groups system bursts in the full log', () => {
    const entries = buildHistory(records).entries;

    expect(visibleEntries(entries, 'workflow', '', false).map((entry) => entry.index)).toEqual([0, 9, 10, 11]);
    expect(visibleEntries(entries, 'answer', '', true).map((entry) => entry.index)).toEqual([3, 5]);
    expect(visibleEntries(entries, 'all', 'duree_enq', false).map((entry) => entry.index)).toEqual([8]);
    const full = visibleEntries(entries, 'all', '', false);
    expect(full.length).toBe(11);
    expect(full.find((entry) => entry.grouped === 2)?.label).toBe('Recalculs automatiques');
  });

  it('exports a semicolon CSV with escaped values', () => {
    const csv = historyCsv(buildHistory([record(0, 'CommentSet', '2026-11-13T11:49:15Z', { comment: 'Âge "à vérifier"' })]).entries);

    expect(csv.startsWith('﻿"N°";"Date (UTC)"')).toBeTrue();
    expect(csv).toContain('"Commentaire : Âge ""à vérifier"""');
  });
});
