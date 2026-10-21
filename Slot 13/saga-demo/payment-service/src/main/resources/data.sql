IF NOT EXISTS (SELECT 1 FROM wallet WHERE customer_id = 'C1') INSERT INTO wallet (customer_id, balance) VALUES ('C1', 5000);
IF NOT EXISTS (SELECT 1 FROM wallet WHERE customer_id = 'C2') INSERT INTO wallet (customer_id, balance) VALUES ('C2', 100);
