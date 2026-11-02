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
});
