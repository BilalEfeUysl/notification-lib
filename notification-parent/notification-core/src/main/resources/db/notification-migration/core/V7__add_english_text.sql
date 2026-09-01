-- Bildirim iceriginin dil sutunlari.
--
-- classification / message -> classification_tr / message_tr olarak yeniden
-- adlandirilir; boylece Ingilizce (_en) sutunlariyla SIMETRIK olurlar.
-- _tr sutunlari NOT NULL kalir = VARSAYILAN metin: kutuphaneyi kullanan uygulama
-- buraya hangi dilde yazarsa o gosterilir; Ingilizcesi yoksa herkes bunu gorur.
--
-- classification_en / message_en = opsiyonel Ingilizce karsilik. Arayuz dili
-- 'en' VE bu sutunlar doluysa bunlar gosterilir; aksi halde _tr'ye dusulur.
--
-- CHECK: bir dil ya TAM (baslik + mesaj birlikte) ya da HIC yok - yarim olamaz.

ALTER TABLE ${notificationSchema}.${notificationTable} RENAME COLUMN classification TO classification_tr;
ALTER TABLE ${notificationSchema}.${notificationTable} RENAME COLUMN message        TO message_tr;

ALTER TABLE ${notificationSchema}.${notificationTable}
    ADD COLUMN IF NOT EXISTS classification_en VARCHAR(128),
    ADD COLUMN IF NOT EXISTS message_en        TEXT;

ALTER TABLE ${notificationSchema}.${notificationTable}
    DROP CONSTRAINT IF EXISTS chk_${notificationTable}_english_text_complete;

ALTER TABLE ${notificationSchema}.${notificationTable}
    ADD CONSTRAINT chk_${notificationTable}_english_text_complete
    CHECK ((classification_en IS NULL AND message_en IS NULL)
        OR (classification_en IS NOT NULL AND message_en IS NOT NULL));
