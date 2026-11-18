import { AutoRejectRule } from '../../../../core/models/dashboard.models';
import { cardProblem, cardsFromRules, emptyCard, rulesFromCards, rulesSignature } from './rule-cards';

function rule(change: Partial<AutoRejectRule>): AutoRejectRule {
  return { id: 1, type: 'MIN_DURATION', variable: null, minValue: null, maxValue: null, message: null, enabled: true, ...change };
}

describe('rule cards', () => {
  const stored: AutoRejectRule[] = [
    rule({ type: 'MIN_DURATION', minValue: 15 }),
    rule({ type: 'REQUIRED_ANSWER', variable: 's00q01', message: 'Compléter' }),
    rule({ type: 'VALUE_RANGE', variable: 'B5', minValue: 15, maxValue: 99 }),
    rule({ type: 'REQUIRED_ANSWER', variable: 'B11', message: 'Compléter' }),
    rule({ type: 'VALUE_RANGE', variable: 'grappe', minValue: 1, maxValue: null })
  ];

  it('regroupe les règles par type : une carte multi-questions et une carte multi-plages', () => {
    const cards = cardsFromRules(stored);

    expect(cards.map((card) => card.type)).toEqual(['MIN_DURATION', 'REQUIRED_ANSWER', 'VALUE_RANGE']);
    expect(cards[1].variables).toEqual(['s00q01', 'B11']);
    expect(cards[1].message).toBe('Compléter');
    expect(cards[2].ranges).toEqual([
      { variable: 'B5', minValue: 15, maxValue: 99 },
      { variable: 'grappe', minValue: 1, maxValue: null }
    ]);
  });

  it('redonne une règle par question, avec le message et l’activation de la carte', () => {
    const cards = cardsFromRules(stored);
    cards[1] = { ...cards[1], enabled: false, message: '  Merci de compléter  ' };

    const rules = rulesFromCards(cards);

    expect(rules.length).toBe(5);
    expect(rules.filter((item) => item.type === 'REQUIRED_ANSWER'))
      .toEqual([jasmine.objectContaining({ variable: 's00q01', enabled: false, message: 'Merci de compléter' }),
        jasmine.objectContaining({ variable: 'B11', enabled: false, message: 'Merci de compléter' })]);
    expect(rulesSignature(rulesFromCards(cardsFromRules(stored)))).toBe(rulesSignature(rulesFromCards(cardsFromRules(stored))));
  });

  it('signale la carte à compléter avant l’envoi', () => {
    expect(cardProblem([emptyCard('REQUIRED_ANSWER')])).toContain('au moins une question');
    expect(cardProblem([emptyCard('VALUE_RANGE')])).toContain('question de chaque ligne');
    expect(cardProblem([{ ...emptyCard('VALUE_RANGE'), ranges: [{ variable: 'B5', minValue: null, maxValue: null }] }]))
      .toBe('Indiquez un minimum ou un maximum pour « B5 ».');
    expect(cardProblem([{ ...emptyCard('VALUE_RANGE'), ranges: [{ variable: 'B5', minValue: 99, maxValue: 15 }] }]))
      .toBe('Le minimum dépasse le maximum pour « B5 ».');
    expect(cardProblem(cardsFromRules(stored))).toBeNull();
  });
});
