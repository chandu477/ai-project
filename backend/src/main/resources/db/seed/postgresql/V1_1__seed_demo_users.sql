-- Demo data only (excluded in the prod profile): 1000 users with 0-15 orders each,
-- monthly salaries of 30,000-149,999 and 0-30 years of experience.
INSERT INTO app_user (username, monthly_salary, years_of_experience)
SELECT (ARRAY['alice', 'bob', 'carol', 'dave', 'erin', 'frank', 'grace', 'heidi', 'ivan', 'judy'])[x % 10 + 1]
           || '.' || LPAD(x::TEXT, 4, '0'),
       30000 + (x * 7919) % 120000,
       (x * 13) % 31
FROM generate_series(1, 1000) AS x;

INSERT INTO customer_order (user_id, amount)
SELECT u.id, 10 + (u.id * r * 37) % 490
FROM app_user u
JOIN generate_series(1, 15) AS r ON r <= (u.id * 31) % 16;
