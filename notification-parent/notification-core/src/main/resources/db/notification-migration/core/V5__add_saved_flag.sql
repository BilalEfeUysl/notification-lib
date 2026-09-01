-- Kullanicinin bir bildirimi "kaydetmesi" icin. Yeni kayitlar varsayilan FALSE gelir.
ALTER TABLE ${notificationSchema}.${notificationTable}
    ADD COLUMN IF NOT EXISTS saved BOOLEAN NOT NULL DEFAULT FALSE;
