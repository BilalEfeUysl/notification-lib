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
package io.github.bilalefeuysl.notification.websocket.identity;

import io.github.bilalefeuysl.notification.core.model.NotificationIdentity;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Varsayilan implementasyon. Once header'lardan (X-User-Id / X-User-Roles)
 * okumayi dener - ornegin Postman gibi araclarla veya sunucudan sunucuya
 * baglantilarda bu calisir. Ama tarayicinin yerlesik WebSocket API'si
 * BAGLANTI KURARKEN OZEL HEADER EKLEMEYE IZIN VERMEZ - bu yuzden header
 * yoksa, ayni bilgiyi query parametrelerinden (?userId=...&roles=...)
 * okumayi dener. Gercek tarayici uygulamalari bu ikinci yolu kullanmak
 * zorunda kalacak.
 */
public class HeaderNotificationIdentityResolver implements NotificationIdentityResolver {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String ROLES_HEADER = "X-User-Roles";
    private static final String USER_ID_QUERY_PARAM = "userId";
    private static final String ROLES_QUERY_PARAM = "roles";

    @Override
    public NotificationIdentity resolve(ServerHttpRequest request) {
        String userId = firstHeader(request, USER_ID_HEADER);
        String rolesRaw = firstHeader(request, ROLES_HEADER);

        if (userId == null || userId.isBlank()) {
            MultiValueMap<String, String> query =
                    UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
            userId = query.getFirst(USER_ID_QUERY_PARAM);
            rolesRaw = query.getFirst(ROLES_QUERY_PARAM);
        }

        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException(
                    "targeting.enabled=true ama WebSocket baglanti istegi ne " + USER_ID_HEADER
                            + " header'ini ne de " + USER_ID_QUERY_PARAM + " query parametresini iceriyor.");
        }

        return new NotificationIdentity(userId, parseRoles(rolesRaw));
    }

    private String firstHeader(ServerHttpRequest request, String name) {
        List<String> values = request.getHeaders().get(name);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }

    private Set<String> parseRoles(String rolesRaw) {
        if (rolesRaw == null || rolesRaw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(rolesRaw.split(","))
                .map(String::trim)
                .filter(r -> !r.isEmpty())
                .collect(Collectors.toSet());
    }
}