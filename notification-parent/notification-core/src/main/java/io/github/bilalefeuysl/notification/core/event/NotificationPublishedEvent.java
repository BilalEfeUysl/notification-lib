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
package io.github.bilalefeuysl.notification.core.event;

import io.github.bilalefeuysl.notification.core.model.Notification;

/**
 * Bir bildirim basariyla kaydedildiginde yayinlanan olay.
 * Kullanan uygulama, kutuphaneye hic dokunmadan @EventListener ile bu olayi
 * dinleyip kendi ek islemlerini (e-posta, metrik, log vb.) ekleyebilir:
 *
 * <pre>
 * {@literal @}EventListener
 * public void onNotificationPublished(NotificationPublishedEvent event) {
 *     Notification n = event.notification();
 *     // ...
 * }
 * </pre>
 */
public record NotificationPublishedEvent(Notification notification) {
}