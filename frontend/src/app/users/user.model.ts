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
