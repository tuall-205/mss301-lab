-- password of all 3 accounts is 123456 (BCrypt)
INSERT INTO customer (customer_name, telephone, email, customer_birthday, customer_status, password) VALUES
(N'Nguyễn Văn An', '0905123456', 'an@gmail.com',   '2002-05-10', 'ACTIVE',   '$2a$10$dmoDdVpWYdqLarqBfkYQteoq1YORLC5LLMd55bpomZ3EarS/vtjtW'),
(N'Trần Thị Bình', '0914234567', 'binh@gmail.com', '2003-08-21', 'ACTIVE',   '$2a$10$dmoDdVpWYdqLarqBfkYQteoq1YORLC5LLMd55bpomZ3EarS/vtjtW'),
(N'Lê Minh Chi',   '0935345678', 'chi@gmail.com',  '2001-12-01', 'INACTIVE', '$2a$10$dmoDdVpWYdqLarqBfkYQteoq1YORLC5LLMd55bpomZ3EarS/vtjtW');
