import { Signal, WritableSignal, computed, signal } from '@angular/core';

/** Recherche insensible à la casse et aux accents. */
export function normalizeText(value: string | null | undefined): string {
  return (value ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .trim();
}

export interface TableState<T> {
  readonly search: WritableSignal<string>;
  readonly page: Signal<number>;
  readonly pageSize: number;
  readonly filtered: Signal<T[]>;
  readonly visible: Signal<T[]>;
  readonly total: Signal<number>;
  setSearch(value: string): void;
  setPage(page: number): void;
}

/**
 * Recherche et pagination côté client d'un tableau. `text` renvoie le texte cherché pour une ligne.
 * La page revient à 1 quand la recherche change et reste dans les bornes si les lignes changent.
 */
export function createTableState<T>(rows: () => T[], text: (row: T) => string, pageSize = 10): TableState<T> {
  const search = signal('');
  const requestedPage = signal(1);
  const filtered = computed(() => {
    const query = normalizeText(search());
    const all = rows() ?? [];
    return query ? all.filter((row) => normalizeText(text(row)).includes(query)) : all;
  });
  const page = computed(() => Math.min(requestedPage(), Math.max(1, Math.ceil(filtered().length / pageSize))));
  const visible = computed(() => filtered().slice((page() - 1) * pageSize, page() * pageSize));
  return {
    search,
    page,
    pageSize,
    filtered,
    visible,
    total: computed(() => filtered().length),
    setSearch: (value: string) => {
      search.set(value);
      requestedPage.set(1);
    },
    setPage: (value: number) => requestedPage.set(value)
  };
}
