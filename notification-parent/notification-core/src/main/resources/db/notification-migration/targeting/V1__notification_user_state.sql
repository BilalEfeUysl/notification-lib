CREATE TABLE IF NOT EXISTS ${notificationSchema}.notification_user_state (
    notification_id UUID         NOT NULL REFERENCES ${notificationSchema}.${notificationTable}(id),
    user_id         VARCHAR(128) NOT NULL,
    read_at         TIMESTAMPTZ,
    hidden_at       TIMESTAMPTZ,
    PRIMARY KEY (notification_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_notification_user_state_user
    ON ${notificationSchema}.notification_user_state (user_id);