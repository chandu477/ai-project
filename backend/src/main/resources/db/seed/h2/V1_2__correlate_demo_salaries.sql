-- Demo data only: make the seeded salaries depend on experience (plus spread) so salary
-- recommendations are meaningful. Only the seeded rows (ids 1-1000, inserted by V1_1) are touched. Range is about 20,000-145,000.
UPDATE app_user
SET monthly_salary = 30000 + years_of_experience * 3500
                     + MOD(CAST(SUBSTRING(username, POSITION('.' IN username) + 1) AS INT) * 7919, 20000) - 10000
WHERE id <= 1000 AND REGEXP_LIKE(username, '^(alice|bob|carol|dave|erin|frank|grace|heidi|ivan|judy)\.[0-9]{4}$');
