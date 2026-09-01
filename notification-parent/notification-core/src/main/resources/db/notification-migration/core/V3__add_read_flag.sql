-- Bildirimin modalda goruldugunu isaretlemek icin. Yeni kayitlar varsayilan FALSE gelir.
ALTER TABLE ${notificationSchema}.${notificationTable}
    ADD COLUMN IF NOT EXISTS read BOOLEAN NOT NULL DEFAULT FALSE;