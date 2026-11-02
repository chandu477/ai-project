import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subject, catchError, exhaustMap, map, of, switchMap, timeout } from 'rxjs';

import { SalaryRecommendation } from '../../salary/salary-recommendation.model';
import { SalaryRecommendationService } from '../../salary/salary-recommendation.service';
import { CreateUserRequest, ProblemDetail } from '../user.model';
import { UserService } from '../user.service';

type Suggestion =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'ready'; years: number; recommendation: SalaryRecommendation };

type SaveResult = { ok: true } | { ok: false; error: unknown };

const BADGE_TEXT: Record<SalaryRecommendation['source'], string> = {
  AI: 'AI suggestion',
  STATISTICAL: 'Statistical estimate (AI unavailable)',
};

// Backend can take ~16s (timeout + retry) before falling back.
const SUGGEST_TIMEOUT_MS = 20_000;

const GENERAL_ERROR = 'Could not save the user. Please try again.';

function fieldErrors(problem: ProblemDetail | null): Record<string, string> {
  return problem?.errors ?? {};
}

function halfYearStep(control: AbstractControl): ValidationErrors | null {
  const value: unknown = control.value;
  return typeof value !== 'number' || Number.isInteger(value * 2) ? null : { step: true };
}

// Matches DECIMAL(12,2): at most 10 integer digits and 2 decimals.
function salaryPrecision(control: AbstractControl): ValidationErrors | null {
  const value: unknown = control.value;
  if (value === null || value === '') {
    return null;
  }
  return /^\d{1,10}(\.\d{1,2})?$/.test(String(value)) ? null : { precision: true };
}

@Component({
  selector: 'app-add-user',
  imports: [ReactiveFormsModule, DecimalPipe, RouterLink],
  templateUrl: './add-user.component.html',
  styleUrl: './add-user.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddUserComponent {
  private readonly userService = inject(UserService);
  private readonly recommendations = inject(SalaryRecommendationService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly form = new FormGroup({
    username: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(3), Validators.maxLength(50), Validators.pattern(/^[a-z0-9._-]+$/)],
    }),
    yearsOfExperience: new FormControl<number | null>(null, [
      Validators.required,
      Validators.min(0),
      Validators.max(60),
      halfYearStep,
    ]),
    monthlySalary: new FormControl<number | null>(null, [Validators.required, Validators.min(0.01), salaryPrecision]),
  });

  protected readonly suggestion = signal<Suggestion>({ status: 'idle' });
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);

  private readonly currentYears = toSignal(this.form.controls.yearsOfExperience.valueChanges, {
    initialValue: this.form.controls.yearsOfExperience.value,
  });

  protected readonly staleYears = computed(() => {
    const suggestion = this.suggestion();
    return suggestion.status === 'ready' && suggestion.years !== this.currentYears() ? suggestion.years : null;
  });

  protected readonly badgeText = computed(() => {
    const suggestion = this.suggestion();
    return suggestion.status === 'ready' ? BADGE_TEXT[suggestion.recommendation.source] : '';
  });

  private readonly suggestRequests = new Subject<number>();
  private readonly saveRequests = new Subject<CreateUserRequest>();

  constructor() {
    // switchMap: a newer suggestion request cancels the older one.
    this.suggestRequests
      .pipe(
        switchMap((years) =>
          this.recommendations.recommend(years).pipe(
            timeout(SUGGEST_TIMEOUT_MS),
            map((recommendation): Suggestion => ({ status: 'ready', years, recommendation })),
            catchError(() => of<Suggestion>({ status: 'error' })),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((suggestion) => this.suggestion.set(suggestion));

    // exhaustMap: clicks while a save is in flight are ignored.
    this.saveRequests
      .pipe(
        exhaustMap((request) =>
          this.userService.createUser(request).pipe(
            map((): SaveResult => ({ ok: true })),
            catchError((error: unknown) => of<SaveResult>({ ok: false, error })),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => this.onSaved(result));
  }

  protected suggest(): void {
    const years = this.form.controls.yearsOfExperience;
    if (years.invalid || years.value === null) {
      return;
    }
    this.suggestion.set({ status: 'loading' });
    this.suggestRequests.next(years.value);
  }

  protected useSuggestedSalary(): void {
    const suggestion = this.suggestion();
    if (suggestion.status === 'ready') {
      const salary = this.form.controls.monthlySalary;
      salary.setValue(suggestion.recommendation.recommendedMonthlySalary);
      salary.markAsDirty();
    }
  }

  protected save(): void {
    const { username, yearsOfExperience, monthlySalary } = this.form.getRawValue();
    if (this.form.invalid || yearsOfExperience === null || monthlySalary === null) {
      this.form.markAllAsTouched();
      return;
    }
    this.saveError.set(null);
    this.saving.set(true);
    this.saveRequests.next({ username, yearsOfExperience, monthlySalary });
  }

  private onSaved(result: SaveResult): void {
    this.saving.set(false);
    if (result.ok) {
      void this.router.navigate(['/users']);
      return;
    }
    this.showSaveError(result.error);
  }

  private showSaveError(error: unknown): void {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    if (status === 409) {
      this.form.controls.username.setErrors({ usernameTaken: true });
      this.form.controls.username.markAsTouched();
      return;
    }
    const problem = error instanceof HttpErrorResponse ? (error.error as ProblemDetail | null) : null;
    if (status === 400) {
      const unmapped = this.applyFieldErrors(fieldErrors(problem));
      if (unmapped) {
        this.saveError.set(problem?.detail ?? GENERAL_ERROR);
      }
      return;
    }
    this.saveError.set(GENERAL_ERROR);
  }

  // Returns true when nothing could be mapped onto a field.
  private applyFieldErrors(errors: Record<string, string>): boolean {
    let mapped = false;
    for (const [field, message] of Object.entries(errors)) {
      const control = this.form.get(field);
      if (control) {
        control.setErrors({ server: message });
        control.markAsTouched();
        mapped = true;
      }
    }
    return !mapped;
  }
}
