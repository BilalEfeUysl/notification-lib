-- read_at/hidden_at ile ayni desen: NULL degilse o kullanici bu bildirimi
-- kaydetmis demektir. hidden_at/read_at'ten farkli olarak "saved" GERI
-- ALINABILIR bir islem oldugu icin (kaydet/kaydi kaldir), NULL'a geri
-- donebilir - tek yonlu degil.
ALTER TABLE ${notificationSchema}.notification_user_state
    ADD COLUMN IF NOT EXISTS saved_at TIMESTAMPTZ;
