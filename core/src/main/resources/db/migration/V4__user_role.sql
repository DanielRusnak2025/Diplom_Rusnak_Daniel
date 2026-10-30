-- Step 1: add role column nullable (backfill before NOT NULL)
ALTER TABLE `user`
    ADD COLUMN `role` VARCHAR(20) NULL;

-- Step 2: backfill from is_admin
UPDATE `user`
    SET `role` = CASE WHEN `is_admin` = 1 THEN 'ADMIN' ELSE 'USER' END;

-- Step 3: NOT NULL + DEFAULT + CHECK
ALTER TABLE `user`
    MODIFY COLUMN `role` VARCHAR(20) NOT NULL DEFAULT 'USER',
    ADD CONSTRAINT chk_user_role CHECK (`role` IN ('USER','MODERATOR','ADMIN'));

-- Step 4: drop is_admin
ALTER TABLE `user` DROP COLUMN `is_admin`;
