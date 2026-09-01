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
package io.github.bilalefeuysl.notification.websocket.event;

import io.github.bilalefeuysl.notification.core.model.Notification;

import java.time.Instant;
import java.util.UUID;

import java.util.Map;

public record NotificationCreatedEvent(String event, Payload payload) {

    public record Payload(
            UUID id,
            String classification,
            String message,
            String classificationEn,
            String messageEn,
            String type,
            String priority,
            boolean read,
            boolean saved,
            Instant createdAt,
            Map<String, Object> metadata,
            String sourceDeviceId
    ) {}

    public static NotificationCreatedEvent of(Notification notification) {
        return new NotificationCreatedEvent(
                "NOTIFICATION_CREATED",
                new Payload(
                        notification.id(),
                        notification.classification(),
                        notification.message(),
                        notification.classificationEn(),
                        notification.messageEn(),
                        notification.type(),
                        notification.priority().name(),
                        notification.read(),
                        notification.saved(),
                        notification.createdAt(),
                        notification.metadata(),
                        notification.sourceDeviceId()));
    }
}