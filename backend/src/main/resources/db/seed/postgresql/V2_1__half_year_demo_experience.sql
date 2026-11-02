-- Demo data only: give the seeded rows (ids 1-1000, inserted by V1_1) half-year experience of 0-30 years
-- and recompute the demo salary so it keeps rising with experience. Real users are not touched.
UPDATE app_user
SET years_of_experience = ((id * 13) % 61) / 2.0,
    monthly_salary = 30000 + (((id * 13) % 61) / 2.0) * 3500 + (id * 7919) % 20000 - 10000
WHERE id <= 1000 AND username ~ '^(alice|bob|carol|dave|erin|frank|grace|heidi|ivan|judy)\.[0-9]{4}$';
