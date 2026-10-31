-- NOTE: passwords here are plaintext stubs for seeding a fresh db_use_test.
-- V3_2 hashes them to argon2id on first --migrate run.
-- Do NOT run against a DB that already has V3+ migrations applied.
-- After V6_1: set languages/regions via UserService.setLanguages/setRegions,
-- or INSERT directly into user_language / user_region after seeding.
INSERT INTO `user`
(nick, password, registration_date, email, read_articles_count)
VALUES
('user1','pass1','2024-01-01','u1@mail.com',5),
('user2','pass2','2024-01-02','u2@mail.com',12),
('user3','pass3','2024-01-03','u3@mail.com',7),
('user4','pass4','2024-01-04','u4@mail.com',3),
('user5','pass5','2024-01-05','u5@mail.com',9),
('user6','pass6','2024-01-06','u6@mail.com',20),
('user7','pass7','2024-01-07','u7@mail.com',1),
('user8','pass8','2024-01-08','u8@mail.com',0),
('user9','pass9','2024-01-09','u9@mail.com',14),
('user10','pass10','2024-01-10','u10@mail.com',6);

SELECT * FROM `user`;
