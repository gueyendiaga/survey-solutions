import { HttpErrorResponse } from '@angular/common/http';

/** Message « detail » d'une réponse d'erreur (ProblemDetail), sinon le message par défaut. */
export function apiErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof HttpErrorResponse) {
    const detail = error.error?.detail;
    if (typeof detail === 'string') {
      return detail;
    }
  }
  return fallback;
}

/** 409 + syncPending : les données de la version sont en cours d'import. */
export function isSyncPending(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 409 && !!error.error?.syncPending;
}

/** Lit le « detail » d'une erreur renvoyée sous forme de Blob (téléchargements). */
export async function blobErrorDetail(error: unknown): Promise<string | null> {
  if (!(error instanceof HttpErrorResponse) || !(error.error instanceof Blob)) {
    return null;
  }
  try {
    const problem: unknown = JSON.parse(await error.error.text());
    if (problem !== null && typeof problem === 'object' && 'detail' in problem) {
      const detail = (problem as { detail: unknown }).detail;
      return typeof detail === 'string' ? detail : null;
    }
  } catch (parseError: unknown) {
    if (!(parseError instanceof SyntaxError)) {
      throw parseError;
    }
  }
  return null;
}

/** Propose le fichier au téléchargement. */
export function saveFile(file: Blob, filename: string): void {
  const url = URL.createObjectURL(file);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
