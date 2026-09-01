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

package io.github.bilalefeuysl.notification.rest.dto;

import java.time.Instant;
import java.util.List;

/**
 * Plan belgesindeki liste yaniti sozlesmesi: items / hasMore / nextBefore.
 * nextPriorityCursor SADECE sort=priority istekleri icin doludur (bkz.
 * PriorityCursor) - varsayilan (tarih sirali) istekler icin her zaman null,
 * istemci onu okumaz/kullanmaz.
 */
public record NotificationPageResponse(
        List<NotificationDto> items,
        boolean hasMore,
        Instant nextBefore,
        String nextPriorityCursor
) {
    public NotificationPageResponse(List<NotificationDto> items, boolean hasMore, Instant nextBefore) {
        this(items, hasMore, nextBefore, null);
    }
}