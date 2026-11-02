import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { SalaryRecommendation } from '../../salary/salary-recommendation.model';
import { AddUserComponent } from './add-user.component';

const RECOMMENDATION: SalaryRecommendation = {
  recommendedMonthlySalary: 82000,
  rangeLow: 74000,
  rangeHigh: 90000,
  rationale: 'Mid-band for 7 years.',
  source: 'AI',
  basedOnUsers: 163,
  bandFromYears: 7,
  bandToYears: 7.5,
  model: 'claude-opus-5-5',
};

describe('AddUserComponent', () => {
  let fixture: ComponentFixture<AddUserComponent>;
  let httpTesting: HttpTestingController;
  let element: HTMLElement;
  let navigate: jasmine.Spy;

  function input(id: string): HTMLInputElement {
    return element.querySelector<HTMLInputElement>(`#${id}`)!;
  }

  function type(id: string, value: string): void {
    const el = input(id);
    el.value = value;
    el.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function blur(id: string): void {
    input(id).dispatchEvent(new Event('blur'));
    fixture.detectChanges();
  }

  function button(label: string): HTMLButtonElement {
    const found = Array.from(element.querySelectorAll('button')).find((b) => b.textContent?.includes(label));
    if (!found) {
      throw new Error(`Button "${label}" not found`);
    }
    return found;
  }

  function click(label: string): void {
    button(label).click();
    fixture.detectChanges();
  }

  function fillValidForm(): void {
    type('username', 'neha.k');
    type('years', '7');
    type('salary', '82000');
  }

  function suggestAndRespond(): void {
    type('years', '7');
    click('Suggest salary');
    httpTesting.expectOne('/api/salary-recommendations').flush(RECOMMENDATION);
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AddUserComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(AddUserComponent);
    element = fixture.nativeElement;
    fixture.detectChanges();
  });

  afterEach(() => httpTesting.verify());

  it('shows field errors after blur and disables Save while invalid', () => {
    expect(button('Save').disabled).toBeTrue();

    type('username', 'Bad Name');
    blur('username');
    expect(element.querySelector('#username-error')?.textContent).toContain('lowercase');
    expect(input('username').getAttribute('aria-invalid')).toBe('true');
    expect(input('username').getAttribute('aria-describedby')).toBe('username-error');

    blur('years');
    expect(element.querySelector('#years-error')?.textContent).toContain('required');
    type('years', '61');
    expect(element.querySelector('#years-error')?.textContent).toContain('between 0 and 60');
    type('years', '2.3');
    expect(element.querySelector('#years-error')?.textContent).toContain('Use steps of 0.5 years');

    type('salary', '0');
    blur('salary');
    expect(element.querySelector('#salary-error')?.textContent).toContain('greater than 0');
    expect(button('Save').disabled).toBeTrue();
  });

  it('shows length and pattern messages for the username in order', () => {
    type('username', 'ab');
    blur('username');
    expect(element.querySelector('#username-error')?.textContent).toContain('3–50 characters');
    type('username', 'a'.repeat(51));
    expect(element.querySelector('#username-error')?.textContent).toContain('3–50 characters');
    type('username', 'Bad Name');
    expect(element.querySelector('#username-error')?.textContent).toContain('lowercase');
    expect(element.querySelector('#username-error')?.textContent).not.toContain('3–50');
    type('username', 'abc');
    expect(element.querySelector('#username-error')?.textContent?.trim()).toBe('');
  });

  it('validates salary precision on the client', () => {
    type('salary', '12.345');
    blur('salary');
    expect(element.querySelector('#salary-error')?.textContent).toContain('2 decimals');
    type('salary', '12345678901');
    expect(element.querySelector('#salary-error')?.textContent).toContain('10 digits');
    type('salary', '1234567890.12');
    expect(element.querySelector('#salary-error')?.textContent?.trim()).toBe('');
    type('salary', '82000.5');
    expect(element.querySelector('#salary-error')?.textContent?.trim()).toBe('');
  });

  it('accepts half-year steps and rejects other fractions', () => {
    type('years', '2.5');
    blur('years');
    expect(element.querySelector('#years-error')?.textContent?.trim()).toBe('');
    expect(button('Suggest salary').disabled).toBeFalse();
    type('years', '2.3');
    expect(element.querySelector('#years-error')?.textContent).toContain('Use steps of 0.5 years');
    expect(button('Suggest salary').disabled).toBeTrue();
  });

  it('shows the widened band and handles decimal staleness', () => {
    type('years', '2.5');
    click('Suggest salary');
    httpTesting
      .expectOne('/api/salary-recommendations')
      .flush({ ...RECOMMENDATION, basedOnUsers: 16, bandFromYears: 2, bandToYears: 3.5 });
    fixture.detectChanges();
    expect(element.querySelector('.suggestion-card')?.textContent?.replace(/\s+/g, ' ')).toContain(
      'Based on 16 users with 2 to 3.5 years',
    );
    expect(element.querySelector('.stale')).toBeNull();
    type('years', '3');
    expect(element.querySelector('.stale')?.textContent).toContain('Suggestion is for 2.5 years');
  });

  it('disables Suggest until experience is valid', () => {
    expect(button('Suggest salary').disabled).toBeTrue();
    type('years', '99');
    expect(button('Suggest salary').disabled).toBeTrue();
    type('years', '7');
    expect(button('Suggest salary').disabled).toBeFalse();
  });

  it('sends the experience and renders the suggestion', () => {
    type('years', '7');
    click('Suggest salary');
    expect(element.textContent).toContain('Analyzing salaries…');

    const req = httpTesting.expectOne('/api/salary-recommendations');
    expect(req.request.body).toEqual({ yearsOfExperience: 7 });
    req.flush(RECOMMENDATION);
    fixture.detectChanges();

    const card = element.querySelector('.suggestion-card')!.textContent!;
    expect(card).toContain('82,000.00');
    expect(card).toContain('74,000.00');
    expect(card).toContain('90,000.00');
    expect(card).toContain('Mid-band for 7 years.');
    expect(card.replace(/\s+/g, ' ')).toContain('Based on 163 users with 7 to 7.5 years');
    expect(card).toContain('AI suggestion');
    expect(element.querySelector('[aria-live="polite"] .suggestion-card')).not.toBeNull();
  });

  it('labels a statistical fallback', () => {
    type('years', '7');
    click('Suggest salary');
    httpTesting
      .expectOne('/api/salary-recommendations')
      .flush({ ...RECOMMENDATION, source: 'STATISTICAL', model: null });
    fixture.detectChanges();

    expect(element.querySelector('.badge')?.textContent).toContain('Statistical estimate (AI unavailable)');
  });

  it('fills the salary field with Use this salary', () => {
    suggestAndRespond();
    click('Use this salary');
    expect(input('salary').value).toBe('82000');
  });

  it('marks the suggestion stale when experience changes', () => {
    suggestAndRespond();
    expect(element.querySelector('.stale')).toBeNull();

    type('years', '9');
    expect(element.querySelector('.stale')?.textContent).toContain('Suggestion is for 7 years — suggest again');
    expect(element.querySelector('.suggestion-card')?.classList).toContain('is-stale');

    type('years', '7');
    expect(element.querySelector('.stale')).toBeNull();
  });

  it('cancels an older suggestion request when a newer one is made', () => {
    type('years', '7');
    click('Suggest salary');
    const first = httpTesting.expectOne('/api/salary-recommendations');
    type('years', '8');
    click('Suggest salary');
    const second = httpTesting.expectOne((r) => r.body.yearsOfExperience === 8);

    expect(first.cancelled).toBeTrue();
    second.flush(RECOMMENDATION);
  });

  it('ends in the error card when the suggestion request hangs', fakeAsync(() => {
    type('years', '7');
    click('Suggest salary');
    const req = httpTesting.expectOne('/api/salary-recommendations');
    tick(19_999);
    fixture.detectChanges();
    expect(element.textContent).toContain('Analyzing salaries…');

    tick(1);
    fixture.detectChanges();
    expect(element.textContent).not.toContain('Analyzing salaries…');
    expect(element.querySelector('.error')?.textContent).toContain('Could not get a salary suggestion');
    expect(req.cancelled).toBeTrue();
  }));

  it('shows an error card on recommendation failure and the form still saves', () => {
    type('years', '7');
    click('Suggest salary');
    httpTesting
      .expectOne('/api/salary-recommendations')
      .flush({ title: 'Unprocessable' }, { status: 422, statusText: 'Unprocessable Entity' });
    fixture.detectChanges();
    expect(element.querySelector('.error')?.textContent).toContain('Could not get a salary suggestion');

    type('username', 'neha.k');
    type('salary', '82000');
    click('Save');
    const req = httpTesting.expectOne('/api/users');
    req.flush({ id: 1 });
    expect(navigate).toHaveBeenCalledWith(['/users']);
  });

  it('posts the user and navigates to the listing', () => {
    fillValidForm();
    click('Save');

    const req = httpTesting.expectOne('/api/users');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ username: 'neha.k', yearsOfExperience: 7, monthlySalary: 82000 });
    req.flush({ id: 1001, ...req.request.body, orderCount: 0 }, { status: 201, statusText: 'Created' });

    expect(navigate).toHaveBeenCalledWith(['/users']);
  });

  it('sends only one request on a double click', () => {
    fillValidForm();
    click('Save');
    expect(button('Saving').disabled).toBeTrue();
    input('username').closest('form')!.dispatchEvent(new Event('submit'));

    const req = httpTesting.expectOne('/api/users');
    req.flush({ id: 1 });
  });

  it('shows "Username already taken" on 409', () => {
    fillValidForm();
    click('Save');
    httpTesting
      .expectOne('/api/users')
      .flush({ title: 'Conflict', status: 409 }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(element.querySelector('#username-error')?.textContent).toContain('Username already taken');
    expect(element.querySelector('[role="alert"]')).toBeNull();
    expect(navigate).not.toHaveBeenCalled();
    expect(button('Save').disabled).toBeTrue();
  });

  it('maps 400 field errors onto the field', () => {
    fillValidForm();
    click('Save');
    httpTesting
      .expectOne('/api/users')
      .flush({ status: 400, errors: { monthlySalary: 'too many digits' } }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(element.querySelector('#salary-error')?.textContent).toContain('too many digits');
    expect(element.querySelector('[role="alert"]')).toBeNull();
  });

  it('maps a 400 on yearsOfExperience onto that field', () => {
    fillValidForm();
    click('Save');
    httpTesting
      .expectOne('/api/users')
      .flush(
        { status: 400, errors: { yearsOfExperience: 'must be a whole number' } },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();

    expect(element.querySelector('#years-error')?.textContent).toContain('must be a whole number');
    expect(element.querySelector('[role="alert"]')).toBeNull();
  });

  it('falls back to the general alert for a 400 without field errors', () => {
    fillValidForm();
    click('Save');
    httpTesting
      .expectOne('/api/users')
      .flush({ status: 400, detail: 'Validation failed' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Validation failed');
  });

  it('shows a general alert for other errors', () => {
    fillValidForm();
    click('Save');
    httpTesting.expectOne('/api/users').flush({ status: 500 }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Could not save the user');
    expect(button('Save').disabled).toBeFalse();
  });
});
