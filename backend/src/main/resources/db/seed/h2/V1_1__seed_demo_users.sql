-- Demo data only (excluded in the prod profile): 1000 users with 0-15 orders each,
-- monthly salaries of 30,000-149,999 and 0-30 years of experience.
INSERT INTO app_user (username, monthly_salary, years_of_experience)
SELECT ARRAY['alice', 'bob', 'carol', 'dave', 'erin', 'frank', 'grace', 'heidi', 'ivan', 'judy'][MOD(X, 10) + 1]
           || '.' || LPAD(CAST(X AS VARCHAR), 4, '0'),
       30000 + MOD(X * 7919, 120000),
       MOD(X * 13, 31)
FROM SYSTEM_RANGE(1, 1000);

INSERT INTO customer_order (user_id, amount)
SELECT u.id, 10 + MOD(u.id * r.X * 37, 490)
FROM app_user u
JOIN SYSTEM_RANGE(1, 15) r ON r.X <= MOD(u.id * 31, 16);
