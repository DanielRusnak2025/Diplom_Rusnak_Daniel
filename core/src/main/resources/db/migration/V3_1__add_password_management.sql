-- V3_1: create password_history; add password-management columns to user.
-- user.password is already VARCHAR(255) (@Column(length=255));
-- argon2id hash is ~97 chars — no resize needed.

CREATE TABLE `password_history` (
    `id`            INT          NOT NULL AUTO_INCREMENT,
    `user_id`       INT          NOT NULL,
    `password_hash` VARCHAR(255) NOT NULL,
    `created_at`    DATETIME(6)  NOT NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_ph_user_created` (`user_id`, `created_at`),
    CONSTRAINT `FK_ph_user`
        FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `user`
    ADD COLUMN `password_changed_at` DATETIME(6) NULL           AFTER `password`,
    ADD COLUMN `failed_login_count`  INT NOT NULL DEFAULT 0     AFTER `password_changed_at`,
    ADD COLUMN `locked_until`        DATETIME(6) NULL           AFTER `failed_login_count`;
