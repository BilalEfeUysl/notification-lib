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
import io.github.bilalefeuysl.notification.core.model.NotificationIdentity;

import java.util.List;
import java.util.UUID;

public interface NotificationBroadcaster {
    void broadcast(Notification notification);

    void broadcastHidden(List<UUID> ids);

    void broadcastAllHidden();

    void broadcastRead(List<UUID> ids);

    /**
     * Hedefli modda: gizleme olayini SADECE bu kullaniciya ait oturumlara
     * yollar. Override edilmezse eski (herkese) davranisa duser.
     */
    default void broadcastHiddenForUser(List<UUID> ids, NotificationIdentity identity) {
        broadcastHidden(ids);
    }

    default void broadcastAllHiddenForUser(NotificationIdentity identity) {
        broadcastAllHidden();
    }

    default void broadcastReadForUser(List<UUID> ids, NotificationIdentity identity) {
        broadcastRead(ids);
    }
}