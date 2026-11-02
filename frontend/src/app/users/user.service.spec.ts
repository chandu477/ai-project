import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Page, UserSummary } from './user.model';
import { UserService } from './user.service';

describe('UserService', () => {
  let service: UserService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(UserService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('requests the users page with sort and direction params', () => {
    const response: Page<UserSummary> = { content: [], page: 2, size: 20, totalElements: 50, totalPages: 3 };
    let result: Page<UserSummary> | undefined;

    service.getUsers({ page: 2, sort: 'monthlySalary', direction: 'desc' }).subscribe((page) => (result = page));

    const req = httpTesting.expectOne((r) => r.url === '/api/users');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('sort')).toBe('monthlySalary');
    expect(req.request.params.get('direction')).toBe('desc');
    req.flush(response);

    expect(result).toEqual(response);
  });

  it('posts a new user', () => {
    const body = { username: 'neha.k', yearsOfExperience: 7, monthlySalary: 82000 };
    let result: UserSummary | undefined;

    service.createUser(body).subscribe((user) => (result = user));

    const req = httpTesting.expectOne('/api/users');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({ id: 1001, orderCount: 0, ...body });
    expect(result?.id).toBe(1001);
  });

  it('gets a user by id', () => {
    service.getUser(5).subscribe();

    const req = httpTesting.expectOne('/api/users/5');
    expect(req.request.method).toBe('GET');
    req.flush({ id: 5, username: 'a', orderCount: 0, monthlySalary: 1, yearsOfExperience: 1 });
  });
});
