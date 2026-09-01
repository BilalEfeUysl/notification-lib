-- audience bilgisi her zaman eklenir (hedefleme kapaliyken de zararsizdir) -
-- DEFAULT 'EVERYONE' sayesinde mevcut kayitlar ve targeting kapali kullanicilar
-- hic etkilenmez.
ALTER TABLE ${notificationSchema}.${notificationTable}
    ADD COLUMN IF NOT EXISTS audience_type VARCHAR(16) NOT NULL DEFAULT 'EVERYONE';
ALTER TABLE ${notificationSchema}.${notificationTable}
    ADD COLUMN IF NOT EXISTS audience_value VARCHAR(128);