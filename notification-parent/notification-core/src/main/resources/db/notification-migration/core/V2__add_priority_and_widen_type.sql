-- type artik serbest metin olabildigi icin sutunu genisletiyoruz.
ALTER TABLE ${notificationSchema}.${notificationTable}
    ALTER COLUMN type TYPE VARCHAR(32);

-- Yeni oncelik sutunu. Mevcut kayitlarda deger olmadigi icin
-- guvenli bir varsayilan (NORMAL) ile ekliyoruz.
ALTER TABLE ${notificationSchema}.${notificationTable}
    ADD COLUMN IF NOT EXISTS priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL';