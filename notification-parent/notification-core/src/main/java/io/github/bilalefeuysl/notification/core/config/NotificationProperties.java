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
package io.github.bilalefeuysl.notification.core.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * application.properties / application.yml icindeki "notification.*" anahtarlari.
 * <p>
 * {@code @Validated}: bozuk ayarlar (orn. negatif limit, bos yol) uygulamanin
 * ACILISINDA net bir hatayla reddedilir - runtime'da alakasiz bir hataya
 * donusmesini beklemek yerine.
 */
@ConfigurationProperties(prefix = "notification")
@Validated
public class NotificationProperties {

    /** Kutuphaneyi tamamen kapatir. {@code false} ise hicbir bean/otomatik-yapilandirma devreye girmez. */
    private boolean enabled = true;

    /** Bildirimlerin yazildigi tablonun adi. */
    @NotBlank
    private String tableName = "notifications";

    /** Tablonun (ve kutuphanenin diger tablolarinin) bulunacagi veritabani semasi. */
    @NotBlank
    private String schema = "public";

    /**
     * Kutuphane acilista kendi tablolarini (Flyway migration'lari ile) olusturup
     * guncellesin mi. Varsayilan {@code true}.
     * <p>
     * {@code false} yapin: (1) kendi {@code NotificationRepository} bean'inizi
     * tanimlayip bildirimleri veritabani DISINDA sakliyorsaniz (orn. bellek-ici,
     * test), ya da (2) kutuphanenin semasini kendiniz yonetiyorsaniz. Kapaliyken
     * kutuphane hicbir DataSource'a dokunmaz ve {@code Flyway} bean'i kaydetmez -
     * boylece hic veritabani olmadan da calisir.
     */
    private boolean initializeSchema = true;

    @Valid
    private final Datasource datasource = new Datasource();
    @Valid
    private final Websocket websocket = new Websocket();
    @Valid
    private final Rest rest = new Rest();
    @Valid
    private final Targeting targeting = new Targeting();
    @Valid
    private final Cors cors = new Cors();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public boolean isInitializeSchema() {
        return initializeSchema;
    }

    public void setInitializeSchema(boolean initializeSchema) {
        this.initializeSchema = initializeSchema;
    }

    public Datasource getDatasource() {
        return datasource;
    }

    public Websocket getWebsocket() {
        return websocket;
    }

    public Rest getRest() {
        return rest;
    }

    public Targeting getTargeting() {
        return targeting;
    }

    public Cors getCors() {
        return cors;
    }

    /**
     * Doldurulursa kutuphane KENDI veritabani baglantisini (ayri bir Hikari havuzu) kurar.
     * Bos birakilirsa uygulamanin mevcut DataSource'u kullanilir.
     */
    public static class Datasource {

        /** JDBC URL. Verilirse username/password ile birlikte ayri bir baglanti havuzu kurulur. */
        private String url;

        /** Ayri baglanti icin kullanici adi. */
        private String username;

        /** Ayri baglanti icin parola. */
        private String password;

        public boolean isConfigured() {
            return url != null && !url.isBlank();
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    /** WebSocket kanali - bildirimlerin tarayiciya canli iletildigi yol. */
    public static class Websocket {

        /** {@code false} ise WebSocket kapatilir; bildirimler yalnizca REST ile okunabilir. */
        private boolean enabled = true;

        /** WebSocket el sikismasinin (handshake) dinlendigi yol. */
        @NotBlank
        private String path = "/ws/notifications";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    /** REST uclari - bildirim listeleme, okundu/gizli isaretleme, kaydetme, arama. */
    public static class Rest {

        /** {@code false} ise REST controller hic kaydedilmez. */
        private boolean enabled = true;

        /** Tum bildirim uclarinin altinda toplandigi taban yol. */
        @NotBlank
        private String basePath = "/api/notifications";

        /** Istemci {@code limit} vermezse donulecek bildirim sayisi. */
        @Min(1)
        private int defaultLimit = 25;

        /** Istemcinin isteyebilecegi en yuksek {@code limit} - bunun ustu bu degere kirpilir. */
        @Min(1)
        private int maxLimit = 100;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBasePath() {
            return basePath;
        }

        public void setBasePath(String basePath) {
            this.basePath = basePath;
        }

        public int getDefaultLimit() {
            return defaultLimit;
        }

        public void setDefaultLimit(int defaultLimit) {
            this.defaultLimit = defaultLimit;
        }

        public int getMaxLimit() {
            return maxLimit;
        }

        public void setMaxLimit(int maxLimit) {
            this.maxLimit = maxLimit;
        }
    }

    /**
     * Hedefli bildirim (audience) ozelligini acar. Varsayilan KAPALI - acildiginda
     * NotificationIdentityResolver bean'i zorunlu hale gelir ve ek bir veritabani
     * tablosu (notification_user_state) olusur. Kapaliyken kutuphane bu ozelligin
     * var oldugunu bile bilmez.
     */
    public static class Targeting {

        /** {@code true} ise bildirimler kisiye ozel hedeflenebilir; her istek bir kimlik (X-User-Id) bekler. */
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Tarayici tabanli capraz-origin (CORS) erisimi. REST uclari VE WebSocket
     * el sikismasi ayni listeyi kullanir.
     * <p>
     * Bos birakilirsa (varsayilan) capraz-origin tarayici erisimi KAPALIDIR -
     * yalnizca backend ile ayni origin'den (ayni protokol+host+port) sunulan
     * bir frontend baglanabilir. Frontend farkli bir origin'deyse (orn. ayri
     * bir alan adi, ya da gelistirmede localhost:5173 ile localhost:8080),
     * o origin'leri buraya ekleyin:
     * <pre>
     * notification:
     *   cors:
     *     allowed-origins:
     *       - https://uygulamam.example.com
     *       - http://localhost:5173
     * </pre>
     * Joker ({@code *}) KASITLI olarak desteklenmez - her origin'e acik birakmak
     * giris yapmis bir kullanicinin tarayicisi uzerinden bildirim akisinin
     * kotu niyetli bir siteye sizmasina yol acar (CSWSH).
     */
    public static class Cors {

        /** Bildirim uclarina/WebSocket'e baglanabilecek capraz-origin adresleri. */
        private List<String> allowedOrigins = new ArrayList<>();

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }


}
