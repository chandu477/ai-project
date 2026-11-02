import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { CreateUserRequest, Page, UserQuery, UserSummary } from './user.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);

  getUsers(query: UserQuery): Observable<Page<UserSummary>> {
    const params = new HttpParams({
      fromObject: { page: query.page, sort: query.sort, direction: query.direction },
    });
    return this.http.get<Page<UserSummary>>('/api/users', { params });
  }

  createUser(request: CreateUserRequest): Observable<UserSummary> {
    return this.http.post<UserSummary>('/api/users', request);
  }

  getUser(id: number): Observable<UserSummary> {
    return this.http.get<UserSummary>(`/api/users/${id}`);
  }
}
