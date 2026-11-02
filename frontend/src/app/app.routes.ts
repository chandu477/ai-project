import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'users' },
  {
    path: 'users',
    title: 'Users',
    loadComponent: () => import('./users/user-list/user-list.component').then((m) => m.UserListComponent),
  },
  {
    path: 'users/new',
    title: 'Add user',
    loadComponent: () => import('./users/add-user/add-user.component').then((m) => m.AddUserComponent),
  },
  { path: '**', redirectTo: 'users' },
];
