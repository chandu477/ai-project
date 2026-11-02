import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Page, UserSummary } from '../user.model';
import { UserListComponent } from './user-list.component';

function pageOf(users: UserSummary[], page = 0, totalPages = 3): Page<UserSummary> {
  return { content: users, page, size: 20, totalElements: totalPages * 20, totalPages };
}

const USERS: UserSummary[] = [
  { id: 1, username: 'alice', orderCount: 3, monthlySalary: 50000, yearsOfExperience: 4 },
  { id: 2, username: 'bob', orderCount: 0, monthlySalary: 72500.5, yearsOfExperience: 12 },
];

describe('UserListComponent', () => {
  let fixture: ComponentFixture<UserListComponent>;
  let httpTesting: HttpTestingController;
  let element: HTMLElement;

  function expectUsersRequest(page: string, sort: string, direction: string): TestRequest {
    const req = httpTesting.expectOne((r) => r.url === '/api/users');
    expect(req.request.params.get('page')).toBe(page);
    expect(req.request.params.get('sort')).toBe(sort);
    expect(req.request.params.get('direction')).toBe(direction);
    return req;
  }

  function render(): void {
    fixture.detectChanges();
  }

  function clickButton(label: string): void {
    const button = Array.from(element.querySelectorAll('button')).find((b) => b.textContent?.includes(label));
    if (!button) {
      throw new Error(`Button "${label}" not found`);
    }
    button.click();
    render();
  }

  function header(label: string): HTMLElement {
    const th = Array.from(element.querySelectorAll('th')).find((h) => h.textContent?.includes(label));
    if (!th) {
      throw new Error(`Header "${label}" not found`);
    }
    return th;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [UserListComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(UserListComponent);
    element = fixture.nativeElement;
    render();
  });

  afterEach(() => httpTesting.verify());

  it('loads the first page sorted by username ascending and renders rows', () => {
    expect(element.querySelector('[role="status"]')?.textContent).toContain('Loading');

    expectUsersRequest('0', 'username', 'asc').flush(pageOf(USERS));
    render();

    const rows = element.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[1].textContent).toContain('bob');
    expect(rows[1].textContent).toContain('72,500.50');
    expect(rows[1].textContent).toContain('12');
    expect(header('Username').getAttribute('aria-sort')).toBe('ascending');
    expect(header('Salary / month').getAttribute('aria-sort')).toBe('none');
    expect(element.querySelector('[role="status"]')?.textContent).not.toContain('Loading');
  });

  it('sorts by a new column ascending, toggles to descending, and resets to the first page', () => {
    expectUsersRequest('0', 'username', 'asc').flush(pageOf(USERS));
    render();
    clickButton('Next');
    expectUsersRequest('1', 'username', 'asc').flush(pageOf(USERS, 1));
    render();

    clickButton('Salary / month');
    expectUsersRequest('0', 'monthlySalary', 'asc').flush(pageOf(USERS));
    render();
    expect(header('Salary / month').getAttribute('aria-sort')).toBe('ascending');

    clickButton('Salary / month');
    expectUsersRequest('0', 'monthlySalary', 'desc').flush(pageOf(USERS));
    render();
    expect(header('Salary / month').getAttribute('aria-sort')).toBe('descending');
  });

  it('sorts by order count', () => {
    expectUsersRequest('0', 'username', 'asc').flush(pageOf(USERS));
    render();

    clickButton('Orders');
    expectUsersRequest('0', 'orderCount', 'asc').flush(pageOf(USERS));
  });

  it('sorts by years of experience', () => {
    expectUsersRequest('0', 'username', 'asc').flush(pageOf(USERS));
    render();

    clickButton('Experience');
    expectUsersRequest('0', 'yearsOfExperience', 'asc').flush(pageOf(USERS));
    render();
    expect(header('Experience').getAttribute('aria-sort')).toBe('ascending');
  });

  it('disables Previous on the first page and Next on the last page', () => {
    expectUsersRequest('0', 'username', 'asc').flush(pageOf(USERS, 0, 1));
    render();

    const buttons = Array.from(element.querySelectorAll<HTMLButtonElement>('.pagination button'));
    expect(buttons.map((b) => b.disabled)).toEqual([true, true]);
    expect(element.querySelector('.pagination')?.textContent).toContain('Page 1 of 1');
  });

  it('shows a no-data card instead of the table and pagination when there are no users', () => {
    expectUsersRequest('0', 'username', 'asc').flush(pageOf([], 0, 0));
    render();

    expect(element.querySelector('.empty-card')?.textContent).toContain('No data available');
    expect(element.querySelector('table')).toBeNull();
    expect(element.querySelector('.pagination')).toBeNull();
  });

  it('shows an error and retries the same query', () => {
    expectUsersRequest('0', 'username', 'asc').flush('boom', { status: 500, statusText: 'Server Error' });
    render();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Could not load users.');

    clickButton('Retry');
    expectUsersRequest('0', 'username', 'asc').flush(pageOf(USERS));
    render();

    expect(element.querySelector('[role="alert"]')).toBeNull();
    expect(element.querySelectorAll('tbody tr').length).toBe(2);
  });
});
