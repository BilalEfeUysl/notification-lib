/*
 * Copyright 2026 Bilal Efe Uysal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.bilalefeuysl.notification.core.broadcast;

import io.github.bilalefeuysl.notification.core.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * Hicbir sey yapmayan varsayilan implementasyon.
 * WebSocket modulu kapaliyken devreye girer, boylece publish()/hide()/hideAll()/markAsRead()
 * calismaya devam eder.
 */
public class NoOpBroadcaster implements NotificationBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(NoOpBroadcaster.class);

    @Override
    public void broadcast(Notification notification) {
        log.debug("Broadcaster kapali, bildirim yayinlanmadi: id={}", notification.id());
    }

    @Override
    public void broadcastHidden(List<UUID> ids) {
        log.debug("Broadcaster kapali, gizleme olayi yayinlanmadi: {} kayit", ids.size());
    }

    @Override
    public void broadcastAllHidden() {
        log.debug("Broadcaster kapali, tumunu gizleme olayi yayinlanmadi");
    }

    @Override
    public void broadcastRead(List<UUID> ids) {
        log.debug("Broadcaster kapali, okundu olayi yayinlanmadi: {} kayit", ids.size());
    }
}