export type RecommendationSource = 'AI' | 'STATISTICAL';

export interface SalaryRecommendation {
  recommendedMonthlySalary: number;
  rangeLow: number;
  rangeHigh: number;
  rationale: string;
  source: RecommendationSource;
  basedOnUsers: number;
  bandFromYears: number;
  bandToYears: number;
  model: string | null;
}
