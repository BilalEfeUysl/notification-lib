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
package io.github.bilalefeuysl.notification.rest.identity;

import io.github.bilalefeuysl.notification.core.model.NotificationIdentity;
import io.github.bilalefeuysl.notification.rest.error.InvalidNotificationRequestException;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Varsayilan implementasyon: kimligi HTTP header'larindan okur. Kullanan
 * uygulama kendi authentication sisteminden (JWT, session, ne olursa olsun)
 * bu header'lari doldurur - kutuphane hangi authentication mekanizmasi
 * kullanildigini bilmek zorunda kalmaz.
 */
public class HeaderNotificationIdentityResolver implements NotificationIdentityResolver {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String ROLES_HEADER = "X-User-Roles";

    @Override
    public NotificationIdentity resolve(HttpServletRequest request) {
        String userId = request.getHeader(USER_ID_HEADER);
        if (userId == null || userId.isBlank()) {
            throw new InvalidNotificationRequestException(
                    "targeting.enabled=true ama istekte " + USER_ID_HEADER + " header'i yok. "
                            + "Kullanan uygulama bu header'i doldurmali ya da kendi "
                            + "NotificationIdentityResolver bean'ini tanimlamali.");
        }

        String rolesHeader = request.getHeader(ROLES_HEADER);
        Set<String> roles = (rolesHeader == null || rolesHeader.isBlank())
                ? Set.of()
                : Arrays.stream(rolesHeader.split(","))
                        .map(String::trim)
                        .filter(r -> !r.isEmpty())
                        .collect(Collectors.toSet());

        return new NotificationIdentity(userId, roles);
    }
}