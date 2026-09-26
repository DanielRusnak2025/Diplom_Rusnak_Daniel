INSERT INTO `user`
(nick, password, first_password, registration_date, email, preferred_region, preferred_language, is_admin, read_articles_count)
VALUES
('user1','pass1','pass1','2024-01-01','u1@mail.com','EU','ru',0,5),
('user2','pass2','pass2','2024-01-02','u2@mail.com','EU','en',0,12),
('user3','pass3','pass3','2024-01-03','u3@mail.com','US','en',0,7),
('user4','pass4','pass4','2024-01-04','u4@mail.com','ASIA','ru',0,3),
('user5','pass5','pass5','2024-01-05','u5@mail.com','EU','ro',0,9),
('user6','pass6','pass6','2024-01-06','u6@mail.com','EU','ru',1,20),
('user7','pass7','pass7','2024-01-07','u7@mail.com','US','en',0,1),
('user8','pass8','pass8','2024-01-08','u8@mail.com','ASIA','en',0,0),
('user9','pass9','pass9','2024-01-09','u9@mail.com','EU','ru',0,14),
('user10','pass10','pass10','2024-01-10','u10@mail.com','EU','ro',0,6);

SELECT * FROM `user`;
