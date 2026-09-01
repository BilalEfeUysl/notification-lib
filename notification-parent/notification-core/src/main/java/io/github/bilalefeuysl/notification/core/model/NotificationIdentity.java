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

import java.util.Set;

/**
 * Bir istegi yapan kullanicinin kimligi. Kutuphane bu bilgiyi NEREDEN geldigiyle
 * ilgilenmez (HTTP, WebSocket, ne olursa olsun) - sadece bu iki alani bilir.
 * Gercek "cikarma" (resolve) islemi rest-api ve websocket modullerinde yapilir.
 */
public record NotificationIdentity(String userId, Set<String> roles) {
    public NotificationIdentity {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }
}