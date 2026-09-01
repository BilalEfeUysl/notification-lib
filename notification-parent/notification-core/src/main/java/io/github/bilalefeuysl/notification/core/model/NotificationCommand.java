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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kutuphaneyi kullanan uygulamanin doldurdugu giris nesnesi.
 *
 * <pre>
 * // Basit kullanim (tip serbest metin, oncelik varsayilan NORMAL):
 * notificationService.publish("Sensor Alarmi", "Tank 3 esik asildi", "BAKIM_GEREKLI");
 *
 * // Hazir tiplerden biriyle:
 * NotificationCommand.builder()
 *     .classification("Sensor Alarmi")
 *     .message("Tank 3 esik asildi")
 *     .type(NotificationType.WARNING)   // hazir oneri, .name()'e cevrilir
 *     .priority(NotificationPriority.HIGH)
 *     .build();
 * </pre>
 */
public final class NotificationCommand {

    public static final int CLASSIFICATION_MAX_LENGTH = 128;
    public static final int SOURCE_DEVICE_ID_MAX_LENGTH = 128;
    public static final int TYPE_MAX_LENGTH = 32;

    private final String classification;
    private final String message;
    private final String classificationEn;
    private final String messageEn;
    private final String type;
    private final NotificationPriority priority;
    private final String sourceDeviceId;
    private final Map<String, Object> metadata;
    private final NotificationAudience audience;

    private NotificationCommand(Builder builder) {
        this.classification = builder.classification;
        this.message = builder.message;
        this.classificationEn = builder.classificationEn;
        this.messageEn = builder.messageEn;
        this.type = builder.type;
        this.priority = builder.priority;
        this.sourceDeviceId = builder.sourceDeviceId;
        this.metadata = Map.copyOf(builder.metadata);
        this.audience = builder.audience;
    }

    /** Varsayilan baslik (zorunlu). */
    public String classification() {
        return classification;
    }

    /** Varsayilan icerik (zorunlu). */
    public String message() {
        return message;
    }

    /** Opsiyonel Ingilizce baslik; verilmediyse null. */
    public String classificationEn() {
        return classificationEn;
    }

    /** Opsiyonel Ingilizce icerik; verilmediyse null. */
    public String messageEn() {
        return messageEn;
    }

    public String type() {
        return type;
    }

    public NotificationPriority priority() {
        return priority;
    }

    public String sourceDeviceId() {
        return sourceDeviceId;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public NotificationAudience audience() {
        return audience;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String classification;
        private String message;
        private String classificationEn;
        private String messageEn;
        private String type;
        private NotificationPriority priority;
        private String sourceDeviceId;
        private final Map<String, Object> metadata = new LinkedHashMap<>();
        private NotificationAudience audience;

        /** Bildirimin basligi. Zorunludur, en fazla 128 karakter. */
        public Builder classification(String classification) {
            this.classification = classification;
            return this;
        }

        /** Bildirimin icerigi. Zorunludur. */
        public Builder message(String message) {
            this.message = message;
            return this;
        }

        /**
         * Opsiyonel: bildirimin Ingilizce basligi. Verilirse {@link #messageEn(String)}
         * de verilmelidir (bir dil ya tam ya hic). Arayuz dili "en" olan kullanicilar
         * bunu gorur; diger herkes {@link #classification(String)}'i gorur.
         */
        public Builder classificationEn(String classificationEn) {
            this.classificationEn = classificationEn;
            return this;
        }

        /** Opsiyonel: bildirimin Ingilizce icerigi. Bkz. {@link #classificationEn(String)}. */
        public Builder messageEn(String messageEn) {
            this.messageEn = messageEn;
            return this;
        }

        /** Serbest bir tip metni verir (orn. "BAKIM_GEREKLI"). Onceden tanimli olmasi gerekmez. */
        public Builder type(String type) {
            this.type = type;
            return this;
        }

        /** Hazir tiplerden (INFO/SUCCESS/WARNING/ERROR) birini secmek icin kisayol. */
        public Builder type(NotificationType type) {
            this.type = (type == null) ? null : type.name();
            return this;
        }

        /** Opsiyonel: oncelik. Belirtilmezse NORMAL kullanilir. */
        public Builder priority(NotificationPriority priority) {
            this.priority = priority;
            return this;
        }

        /** Opsiyonel: bildirimi tetikleyen cihaz/kaynak kimligi. En fazla 128 karakter. */
        public Builder sourceDeviceId(String sourceDeviceId) {
            this.sourceDeviceId = sourceDeviceId;
            return this;
        }

        /** Opsiyonel: serbest ek veri. Onceki metadataEntry() cagrilarini siler. */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata.clear();
            if (metadata != null) {
                this.metadata.putAll(metadata);
            }
            return this;
        }

        /** Opsiyonel: metadata'ya tek bir anahtar-deger cifti ekler. */
        public Builder metadataEntry(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        /**
         * Opsiyonel: bu bildirim kime gitsin. Belirtilmezse Everyone() kullanilir -
         * targeting.enabled kapaliyken bu alanin hicbir etkisi yoktur.
         */
        public Builder audience(NotificationAudience audience) {
            this.audience = audience;
            return this;
        }

        public NotificationCommand build() {
            classification = requireText(classification, "classification");
            message = requireText(message, "message");

            // Ingilizce metin: opsiyonel ama verilirse ikisi birden verilmeli.
            classificationEn = blankToNull(classificationEn);
            messageEn = blankToNull(messageEn);
            if ((classificationEn == null) != (messageEn == null)) {
                throw new IllegalArgumentException(
                        "classificationEn ve messageEn ya birlikte verilmeli ya hic verilmemeli");
            }
            if (classificationEn != null && classificationEn.length() > CLASSIFICATION_MAX_LENGTH) {
                throw new IllegalArgumentException(
                        "classificationEn en fazla " + CLASSIFICATION_MAX_LENGTH + " karakter olabilir");
            }

            if (type == null || type.isBlank()) {
                type = NotificationType.INFO.name();
            } else {
                type = type.trim();
                if (type.length() > TYPE_MAX_LENGTH) {
                    throw new IllegalArgumentException(
                            "type en fazla " + TYPE_MAX_LENGTH + " karakter olabilir");
                }
            }

            if (priority == null) {
                priority = NotificationPriority.NORMAL;
            }

            if (classification.length() > CLASSIFICATION_MAX_LENGTH) {
                throw new IllegalArgumentException(
                        "classification en fazla " + CLASSIFICATION_MAX_LENGTH + " karakter olabilir");
            }
            if (sourceDeviceId != null) {
                sourceDeviceId = sourceDeviceId.trim();
                if (sourceDeviceId.isEmpty()) {
                    sourceDeviceId = null;
                } else if (sourceDeviceId.length() > SOURCE_DEVICE_ID_MAX_LENGTH) {
                    throw new IllegalArgumentException(
                            "sourceDeviceId en fazla " + SOURCE_DEVICE_ID_MAX_LENGTH + " karakter olabilir");
                }
            }
            if (audience == null) {
                audience = new NotificationAudience.Everyone();
            }
            return new NotificationCommand(this);
        }

        private static String requireText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " zorunludur ve bos olamaz");
            }
            return value.trim();
        }

        /** Bos/whitespace metni null'a cevirir, dolu metni trim'ler. */
        private static String blankToNull(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.trim();
        }
    }
}