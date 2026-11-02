-- 25 users: userNN has a monthly salary of NN * 1000, 26 - NN years of experience and MOD(NN, 5) orders.
INSERT INTO app_user (username, monthly_salary, years_of_experience)
SELECT 'user' || LPAD(CAST(X AS VARCHAR), 2, '0'), X * 1000, 26 - X
FROM SYSTEM_RANGE(1, 25);

INSERT INTO customer_order (user_id, amount)
SELECT u.id, 100
FROM app_user u
JOIN SYSTEM_RANGE(1, 4) r ON r.X <= MOD(CAST(SUBSTRING(u.username, 5) AS INT), 5);
