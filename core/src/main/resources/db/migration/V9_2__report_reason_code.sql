ALTER TABLE report
    ADD COLUMN reason_code VARCHAR(30) NULL AFTER rating,
    ADD CONSTRAINT chk_report_rating
        CHECK (rating IN (-1, 0, 1)),
    ADD CONSTRAINT chk_report_complaint
        CHECK (reason_code IS NULL OR rating = -1),
    ADD CONSTRAINT chk_report_reason_code
        CHECK (reason_code IS NULL OR reason_code IN (
            'NOT_INTERESTED_TOPIC', 'UNWANTED_REGION', 'UNWANTED_PUBLISHER',
            'NOT_AN_ARTICLE', 'BROKEN_LINK', 'MISLEADING', 'OTHER'
        )),
    DROP COLUMN reason;
