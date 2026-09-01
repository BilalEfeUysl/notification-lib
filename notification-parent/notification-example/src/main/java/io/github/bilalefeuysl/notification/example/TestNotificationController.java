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
package io.github.bilalefeuysl.notification.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bilalefeuysl.notification.core.model.Notification;
import io.github.bilalefeuysl.notification.core.model.NotificationAudience;
import io.github.bilalefeuysl.notification.core.model.NotificationCommand;
import io.github.bilalefeuysl.notification.core.model.NotificationPriority;
import io.github.bilalefeuysl.notification.core.service.NotificationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/example")
public class TestNotificationController {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TestNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/publish")
    public Notification publish(
            @RequestParam(defaultValue = "Test Bildirimi") String classification,
            @RequestParam(defaultValue = "Bu bir test bildirimidir") String message,
            @RequestParam(required = false) String classificationEn,
            @RequestParam(required = false) String messageEn,
            @RequestParam(defaultValue = "INFO") String type,
            @RequestParam(defaultValue = "NORMAL") NotificationPriority priority,
            @RequestParam(required = false) String sourceDeviceId,
            @RequestParam(required = false) String metadataJson,
            @RequestParam(defaultValue = "EVERYONE") String audienceType,
            @RequestParam(required = false) String audienceValue) {

        Map<String, Object> metadata = parseMetadata(metadataJson);
        NotificationAudience audience = buildAudience(audienceType, audienceValue);

        return notificationService.publish(NotificationCommand.builder()
                .classification(classification)
                .message(message)
                .classificationEn(classificationEn)
                .messageEn(messageEn)
                .type(type)
                .priority(priority)
                .sourceDeviceId(sourceDeviceId)
                .metadata(metadata)
                .audience(audience)
                .build());
    }

    private NotificationAudience buildAudience(String audienceType, String audienceValue) {
        return switch (audienceType) {
            case "SPECIFIC_USER" -> new NotificationAudience.SpecificUser(audienceValue);
            case "ROLE" -> new NotificationAudience.Role(audienceValue);
            default -> new NotificationAudience.Everyone();
        };
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "metadataJson gecerli bir JSON objesi olmali, orn: {\"orderId\":123}");
        }
    }
}