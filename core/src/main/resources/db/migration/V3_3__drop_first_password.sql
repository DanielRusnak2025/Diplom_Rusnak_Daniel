-- Remove the legacy firstPassword column (camelCase, no @Column(name=...) on entity).
-- Hashed into password_history by V3_2; plaintext no longer needed.
ALTER TABLE `user` DROP COLUMN `firstPassword`;
