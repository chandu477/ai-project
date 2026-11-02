import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { SalaryRecommendation } from './salary-recommendation.model';
import { SalaryRecommendationService } from './salary-recommendation.service';

describe('SalaryRecommendationService', () => {
  it('posts the years of experience and returns the recommendation', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(SalaryRecommendationService);
    const httpTesting = TestBed.inject(HttpTestingController);
    const response: SalaryRecommendation = {
      recommendedMonthlySalary: 82000,
      rangeLow: 74000,
      rangeHigh: 90000,
      rationale: 'Mid-band.',
      source: 'AI',
      basedOnUsers: 163,
      bandFromYears: 7,
      bandToYears: 7.5,
      model: 'claude-opus-5-5',
    };
    let result: SalaryRecommendation | undefined;

    service.recommend(7).subscribe((r) => (result = r));

    const req = httpTesting.expectOne('/api/salary-recommendations');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ yearsOfExperience: 7 });
    req.flush(response);
    expect(result).toEqual(response);
    httpTesting.verify();
  });
});
