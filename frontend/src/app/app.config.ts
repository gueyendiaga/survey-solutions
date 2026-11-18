import { ApplicationConfig, provideBrowserGlobalErrorListeners, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { NavigationError, provideRouter, withComponentInputBinding, withNavigationErrorHandler } from '@angular/router';

import { routes } from './app.routes';
import { authExpiryInterceptor, reloadOnStaleBundle } from './core/services/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideHttpClient(withInterceptors([authExpiryInterceptor])),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withNavigationErrorHandler((event: NavigationError) => {
        reloadOnStaleBundle(event.error, event.url);
      })
    )
  ]
};
