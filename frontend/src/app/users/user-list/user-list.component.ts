import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, switchMap } from 'rxjs';

import { Page, SortDirection, SortField, UserQuery, UserSummary } from '../user.model';
import { UserService } from '../user.service';

interface Column {
  field: SortField;
  label: string;
  numeric: boolean;
}

interface LoadResult {
  query: UserQuery;
  page?: Page<UserSummary>;
  failed: boolean;
}

const COLUMNS: readonly Column[] = [
  { field: 'username', label: 'Username', numeric: false },
  { field: 'yearsOfExperience', label: 'Experience (yrs)', numeric: true },
  { field: 'orderCount', label: 'Orders', numeric: true },
  { field: 'monthlySalary', label: 'Salary / month', numeric: true },
];

const ARIA_SORT: Record<SortDirection, string> = { asc: 'ascending', desc: 'descending' };
const SORT_INDICATOR: Record<SortDirection, string> = { asc: '▲', desc: '▼' };

@Component({
  selector: 'app-user-list',
  imports: [DecimalPipe],
  templateUrl: './user-list.component.html',
  styleUrl: './user-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserListComponent {
  private readonly userService = inject(UserService);

  protected readonly query = signal<UserQuery>({ page: 0, sort: 'username', direction: 'asc' });

  // switchMap cancels the in-flight request when the user sorts or pages again.
  private readonly result = toSignal(
    toObservable(this.query).pipe(
      switchMap((query) =>
        this.userService.getUsers(query).pipe(
          map((page): LoadResult => ({ query, page, failed: false })),
          catchError(() => of<LoadResult>({ query, failed: true })),
        ),
      ),
    ),
  );

  protected readonly page = computed(() => this.result()?.page);
  protected readonly failed = computed(() => this.result()?.failed ?? false);
  protected readonly loading = computed(() => this.result()?.query !== this.query());

  protected readonly columns = computed(() => {
    const { sort, direction } = this.query();
    return COLUMNS.map((column) => {
      const active = column.field === sort;
      return {
        ...column,
        ariaSort: active ? ARIA_SORT[direction] : 'none',
        indicator: active ? SORT_INDICATOR[direction] : '',
      };
    });
  });

  protected sortBy(field: SortField): void {
    this.query.update((query) => ({
      page: 0,
      sort: field,
      direction: query.sort === field && query.direction === 'asc' ? 'desc' : 'asc',
    }));
  }

  protected goToPage(page: number): void {
    this.query.update((query) => ({ ...query, page }));
  }

  protected retry(): void {
    this.query.update((query) => ({ ...query }));
  }
}
