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
package io.github.bilalefeuysl.notification.core.model;

/**
 * Bir bildirimin audience'ı ile bir kullanicinin kimligini karsilastirip
 * "bu bildirim bu kullaniciya gitmeli mi" sorusuna cevap verir.
 */
public final class NotificationAudienceMatcher {

    private NotificationAudienceMatcher() {
    }

    public static boolean matches(NotificationAudience audience, NotificationIdentity identity) {
        return switch (audience) {
            case NotificationAudience.Everyone ignored -> true;
            case NotificationAudience.SpecificUser(String userId) -> userId.equals(identity.userId());
            case NotificationAudience.Role(String roleName) -> identity.roles().contains(roleName);
        };
    }
}