-- Experience is recorded in half-year steps (0, 0.5, ... 60). Existing whole-year values are preserved.
ALTER TABLE app_user ALTER COLUMN years_of_experience SET DATA TYPE NUMERIC(3, 1);

ALTER TABLE app_user ADD CONSTRAINT chk_app_user_years_half_year_step
    CHECK (years_of_experience >= 0 AND years_of_experience <= 60
           AND years_of_experience * 2 = FLOOR(years_of_experience * 2));
