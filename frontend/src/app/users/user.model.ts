export type SortField = 'username' | 'orderCount' | 'monthlySalary' | 'yearsOfExperience';
export type SortDirection = 'asc' | 'desc';

export interface UserQuery {
  page: number;
  sort: SortField;
  direction: SortDirection;
}

export interface UserSummary {
  id: number;
  username: string;
  orderCount: number;
  monthlySalary: number;
  yearsOfExperience: number;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CreateUserRequest {
  username: string;
  yearsOfExperience: number;
  monthlySalary: number;
}

// RFC 9457 error body. `errors` maps form field name -> message on 400.
export interface ProblemDetail {
  title?: string;
  detail?: string;
  status?: number;
  errors?: Record<string, string>;
}
