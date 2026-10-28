-- V2: Add surrogate INT AUTO_INCREMENT id to user.
-- Switch report and user_tag_percent FK references from nick to user.id.
-- Fix notification_frequency NULLs. ON DELETE CASCADE on user FKs.

-- ── 0. Drop child FKs referencing user(nick) before restructuring PK
ALTER TABLE `report` DROP FOREIGN KEY `FKg7fcphvch46eg3qto3ymjct2j`;
ALTER TABLE `user_tag_percent` DROP FOREIGN KEY `FKechf7hpfar5tj1wvt8f0cgyg2`;

-- ── 1. Add id to user; populate sequentially ordered by nick
ALTER TABLE `user` ADD COLUMN `id` INT NOT NULL DEFAULT 0 FIRST;
SET @cnt = 0;
UPDATE `user` SET `id` = (@cnt := @cnt + 1) ORDER BY `nick`;

-- ── 2a. Drop PK(nick); set PK(id); add UNIQUE(nick)
ALTER TABLE `user`
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (`id`),
    ADD UNIQUE KEY `uq_user_nick` (`nick`);

-- ── 2b. Enable AUTO_INCREMENT (requires PK to exist first)
ALTER TABLE `user` MODIFY `id` INT NOT NULL AUTO_INCREMENT;

-- ── 3. Fix notification_frequency NULLs → 'never'; make NOT NULL
UPDATE `user` SET `notification_frequency` = 'never'
    WHERE `notification_frequency` IS NULL;
ALTER TABLE `user`
    MODIFY `notification_frequency` VARCHAR(10) NOT NULL DEFAULT 'never';

-- ── 4. report: add user_id; fill; switch UNIQUE + FK (ON DELETE CASCADE); drop user_nick
ALTER TABLE `report` ADD COLUMN `user_id` INT DEFAULT NULL;
UPDATE `report` r JOIN `user` u ON u.`nick` = r.`user_nick` SET r.`user_id` = u.`id`;
ALTER TABLE `report` MODIFY `user_id` INT NOT NULL;
ALTER TABLE `report`
    DROP INDEX `uq_report_user_article`,
    ADD UNIQUE KEY `uq_report_user_article` (`user_id`, `article_id`),
    ADD CONSTRAINT `FK_report_user`
        FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE;
ALTER TABLE `report` DROP COLUMN `user_nick`;

-- ── 5. user_tag_percent: add user_id; fill; switch PK + FK (ON DELETE CASCADE); drop nick
ALTER TABLE `user_tag_percent` ADD COLUMN `user_id` INT DEFAULT NULL;
UPDATE `user_tag_percent` utp JOIN `user` u ON u.`nick` = utp.`nick`
    SET utp.`user_id` = u.`id`;
ALTER TABLE `user_tag_percent` MODIFY `user_id` INT NOT NULL;
ALTER TABLE `user_tag_percent`
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (`user_id`, `tag_id`),
    ADD CONSTRAINT `FK_utp_user`
        FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE;
ALTER TABLE `user_tag_percent` DROP COLUMN `nick`;
