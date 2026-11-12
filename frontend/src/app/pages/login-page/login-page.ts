import { NgOptimizedImage } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login-page',
  imports: [FormsModule, NgOptimizedImage],
  templateUrl: './login-page.html',
  styleUrl: './login-page.css'
})
export class LoginPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  protected readonly connectionError = this.auth.connectionError;
  protected username = '';
  protected password = '';
  protected submitting = signal(false);
  protected error = signal('');

  ngOnInit(): void {
    this.auth.ensureSession().subscribe((session) => {
      if (!session.authenticated) {
        return;
      }
      const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
      void this.router.navigateByUrl(
        returnUrl?.startsWith('/') && !returnUrl.startsWith('//') ? returnUrl : '/'
      );
    });
  }

  protected submit(): void {
    if (this.submitting() || !this.username.trim() || !this.password) {
      return;
    }
    this.error.set('');
    this.submitting.set(true);
    this.auth.login(this.username.trim(), this.password).subscribe({
      next: () => {
        this.password = '';
        this.submitting.set(false);
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        const safeReturnUrl = returnUrl?.startsWith('/') && !returnUrl.startsWith('//')
          ? returnUrl
          : '/';
        void this.router.navigateByUrl(safeReturnUrl);
      },
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 503) {
          this.error.set('');
        } else {
          this.error.set(error instanceof HttpErrorResponse && error.status === 401
            ? 'Identifiant ou mot de passe incorrect.'
            : 'La connexion a échoué. Vérifiez vos identifiants et réessayez.');
        }
        this.password = '';
        this.submitting.set(false);
      }
    });
  }
}
