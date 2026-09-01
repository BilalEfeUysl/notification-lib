-- ${notificationSchema} ve ${notificationTable} Flyway placeholder'laridir.
-- Degerleri NotificationSchemaInitializer icinde konfigurasyondan verilir.

CREATE TABLE IF NOT EXISTS ${notificationSchema}.${notificationTable} (
    id               UUID         PRIMARY KEY,
    classification   VARCHAR(128) NOT NULL,
    message          TEXT         NOT NULL,
    type             VARCHAR(16)  NOT NULL,
    source_device_id VARCHAR(128),
    created_at       TIMESTAMPTZ  NOT NULL,
    visible          BOOLEAN      NOT NULL DEFAULT TRUE,
    metadata         JSONB
);

-- Liste sorgusunun tamami bu indeksten karsilanir:
-- WHERE visible = TRUE ORDER BY created_at DESC
CREATE INDEX IF NOT EXISTS idx_${notificationTable}_visible_created_at
    ON ${notificationSchema}.${notificationTable} (visible, created_at DESC);
