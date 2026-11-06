-- Demo data for make run. The automated tests insert their own data.
INSERT INTO time_deposits (id, plan_type, days, balance)
VALUES (1, 'basic', 45, 10000.00),    -- +8.33 per update
       (2, 'basic', 20, 5000.00),     -- first 30 days: no interest
       (3, 'student', 200, 20000.00), -- +50.00
       (4, 'student', 400, 15000.00), -- older than one year: no interest
       (5, 'premium', 40, 50000.00),  -- premium interest starts after 45 days
       (6, 'premium', 90, 100000.00); -- +416.67

INSERT INTO withdrawals (id, time_deposit_id, amount, date)
VALUES (1, 1, 500.00, '2026-09-15'),
       (2, 3, 1000.00, '2026-10-01'),
       (3, 3, 250.50, '2026-10-20'),
       (4, 6, 2000.00, '2026-08-10');