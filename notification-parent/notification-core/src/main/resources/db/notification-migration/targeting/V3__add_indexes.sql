-- Kisiye ozel "kaydedilenler" sorgusu:
--   ... LEFT JOIN notification_user_state s ... WHERE s.saved_at IS NOT NULL
-- Bu kullanicinin kaydettigi satirlari, tum bildirimleri taramadan bulmak icin.
-- Partial index: yalnizca saved_at dolu satirlar (kucuk bir kume).
CREATE INDEX IF NOT EXISTS idx_notification_user_state_saved
    ON ${notificationSchema}.notification_user_state (user_id)
    WHERE saved_at IS NOT NULL;
