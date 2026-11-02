import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'users' },
  {
    path: 'users',
    title: 'Users',
    loadComponent: () => import('./users/user-list/user-list.component').then((m) => m.UserListComponent),
  },
  { path: '**', redirectTo: 'users' },
];
