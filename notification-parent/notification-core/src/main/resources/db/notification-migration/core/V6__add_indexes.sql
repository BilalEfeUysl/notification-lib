-- Sonradan eklenen sorgu yollari icin indeksler. V1'deki
-- (visible, created_at DESC) indeksi sadece "duz liste" sorgusunu karsiliyordu.
-- Hepsi IF NOT EXISTS: tablo/indeks zaten varsa guvenli.

-- Oncelik filtreli liste: WHERE visible = TRUE AND priority = ? ORDER BY created_at DESC
CREATE INDEX IF NOT EXISTS idx_${notificationTable}_visible_priority_created_at
    ON ${notificationSchema}.${notificationTable} (visible, priority, created_at DESC);

-- Kaydedilenler gorunumu (targeting kapali): WHERE visible = TRUE AND saved = TRUE ORDER BY created_at DESC
-- Partial index: sadece kaydedilmis satirlar; kayitlar tipik olarak kucuk bir kume.
CREATE INDEX IF NOT EXISTS idx_${notificationTable}_saved_created_at
    ON ${notificationSchema}.${notificationTable} (created_at DESC)
    WHERE visible AND saved;

-- Okunmamis sayaci (targeting kapali): WHERE visible = TRUE AND read = FALSE
-- Partial index: yalnizca okunmamislar; okunmus kayitlar zamanla cogunlugu olusturur.
CREATE INDEX IF NOT EXISTS idx_${notificationTable}_unread
    ON ${notificationSchema}.${notificationTable} (created_at DESC)
    WHERE visible AND NOT read;

-- Not: serbest metin arama (q=...) coklu sutunda ILIKE '%...%' kullaniyor;
-- bunu indekslemek pg_trgm eklentisi gerektirir (kurulum superuser ister),
-- kutuphane bunu kullanan uygulamaya dayatmamak icin bilerek eklenmedi.
