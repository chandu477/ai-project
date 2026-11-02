import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { SalaryRecommendation } from './salary-recommendation.model';

@Injectable({ providedIn: 'root' })
export class SalaryRecommendationService {
  private readonly http = inject(HttpClient);

  recommend(yearsOfExperience: number): Observable<SalaryRecommendation> {
    return this.http.post<SalaryRecommendation>('/api/salary-recommendations', { yearsOfExperience });
  }
}
