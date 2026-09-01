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
package io.github.bilalefeuysl.notification.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.bilalefeuysl.notification.core.broadcast.NoOpBroadcaster;
import io.github.bilalefeuysl.notification.core.broadcast.NotificationBroadcaster;
import io.github.bilalefeuysl.notification.core.config.NotificationProperties;
import io.github.bilalefeuysl.notification.core.config.NotificationSchemaInitializer;
import io.github.bilalefeuysl.notification.core.repository.JdbcNotificationRepository;
import io.github.bilalefeuysl.notification.core.repository.NotificationRepository;
import io.github.bilalefeuysl.notification.core.service.DefaultNotificationService;
import io.github.bilalefeuysl.notification.core.service.NotificationService;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import javax.sql.DataSource;

/**
 * Kutuphanenin ana giris noktasi. Bu sinif ve icindeki bean'ler,
 * consuming uygulama sadece dependency'yi ekledigi anda otomatik devreye girer.
 */
@AutoConfiguration
@AutoConfigureAfter({DataSourceAutoConfiguration.class, JacksonAutoConfiguration.class})
@AutoConfigureBefore(FlywayAutoConfiguration.class)
@EnableConfigurationProperties(NotificationProperties.class)
@ConditionalOnProperty(prefix = "notification", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({NotificationWebSocketAutoConfiguration.class, NotificationRestAutoConfiguration.class})
public class NotificationAutoConfiguration {

    /**
     * {@code notification.datasource.url} doluysa kutuphane KENDI (ayri) Hikari
     * baglanti havuzunu kurar. Bu yalnizca HikariCP classpath'te varken devreye
     * girer (nested sinif @ConditionalOnClass ile korunur) - HikariCP starter'da
     * "optional" oldugu icin tuketici uygulama farkli bir havuz kullaniyorsa
     * bu jar'i almak zorunda degil.
     * <p>
     * url BOS ise (varsayilan) bu bean HIC olusturulmaz - kutuphane uygulamanin
     * mevcut DataSource'unu kullanir (bkz. {@link #resolveDataSource}). Neden
     * kosullu: bos url'de uygulamanin DataSource'unu ikinci bir bean adiyla
     * tekrar kaydetmek context'te iki DataSource birakiyordu; hicbiri @Primary
     * olmadigi icin isimle eslesmeyen @Autowired DataSource enjeksiyonlari
     * NoUniqueBeanDefinitionException atiyor, Spring Boot'un
     * @ConditionalOnSingleCandidate(DataSource.class) ile korunan parcalari
     * (JdbcTemplate, transaction manager) sessizce devre disi kaliyordu.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(HikariDataSource.class)
    static class HikariNotificationDataSourceConfiguration {

        @Bean
        @ConditionalOnProperty(prefix = "notification.datasource", name = "url")
        DataSource notificationDataSource(NotificationProperties properties) {
            NotificationProperties.Datasource dsProps = properties.getDatasource();
            HikariDataSource hikari = new HikariDataSource();
            hikari.setJdbcUrl(dsProps.getUrl());
            hikari.setUsername(dsProps.getUsername());
            hikari.setPassword(dsProps.getPassword());
            return hikari;
        }
    }

    /**
     * notification.datasource.url ayarlanmis AMA HikariCP classpath'te YOK -
     * bu durumda kutuphane kendi baglantisini kuramaz. Sessizce uygulamanin
     * DataSource'una geri dusmek yaniltici olurdu (yanlis veritabanina yazmak),
     * o yuzden acilista net bir mesajla duruyoruz (fail fast). Constructor'i
     * calisir calismaz firlatir -> context refresh basarisiz olur.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "notification.datasource", name = "url")
    @ConditionalOnMissingClass("com.zaxxer.hikari.HikariDataSource")
    static class MissingHikariConfiguration {
        MissingHikariConfiguration() {
            throw new IllegalStateException(
                    "notification.datasource.url ayarlandi ama HikariCP (com.zaxxer:HikariCP) "
                            + "classpath'te yok. Kutuphanenin KENDI veritabani baglantisini kurmasi icin "
                            + "HikariCP gerekli. Ya bu bagimliligi ekleyin, ya da notification.datasource.* "
                            + "ayarlarini kaldirip uygulamanizin kendi DataSource'unu kullanin.");
        }
    }

    /**
     * Kutuphanenin ic bean'leri (schema initializer, repository) hangi
     * DataSource'u kullanacak: kendi kurdugumuz {@code notificationDataSource}
     * varsa onu, yoksa uygulamanin DataSource'unu. Ikisi de yoksa net bir hata.
     */
    private static DataSource resolveDataSource(ObjectProvider<DataSource> ownDataSource,
                                                ObjectProvider<DataSource> appDataSource) {
        DataSource own = ownDataSource.getIfAvailable();
        if (own != null) {
            return own;
        }
        DataSource fromApp = appDataSource.getIfAvailable();
        if (fromApp == null) {
            throw new IllegalStateException(
                    "notification.datasource.url tanimlanmadi ve uygulamada baska bir DataSource "
                            + "bulunamadi. Ya notification.datasource.* ozelliklerini doldurun ya da "
                            + "uygulamanizda bir DataSource bean'i tanimlayin.");
        }
        return fromApp;
    }

    /**
     * {@code notification.initialize-schema=false} ise bu bean (ve asagidaki
     * {@code notificationFlyway}) hic olusmaz - kutuphane hicbir DataSource'a
     * dokunmaz. Kendi {@code NotificationRepository}'sini veritabani disinda
     * (bellek-ici, test) tutan ya da semayi kendi yoneten uygulamalar icin.
     */
    @Bean
    @ConditionalOnProperty(prefix = "notification", name = "initialize-schema", havingValue = "true", matchIfMissing = true)
    public NotificationSchemaInitializer notificationSchemaInitializer(
            NotificationProperties properties,
            @Qualifier("notificationDataSource") ObjectProvider<DataSource> notificationDataSource,
            ObjectProvider<DataSource> appDataSource,
            ObjectProvider<FlywayMigrationInitializer> consumerFlywayInitializer) {
        // Tuketicinin KENDI Flyway'i devredeyse (bkz. OnNoConsumerFlywayMigrations:
        // notificationFlyway "bastirici" bean'i kaydedilmemistir, Boot kendi
        // FlywayMigrationInitializer'ini kurar) ONCE onu calistir. Sebep: Boot'un
        // initializer'i baseline-on-migrate=false ile calisir; bizim initializer once
        // calisip semaya tablo yazarsa Boot "Found non-empty schema(s) but no schema
        // history table" hatasiyla acilista coker. getIfAvailable() cagrisi o bean'i
        // (varsa) tam olarak init eder = migration'lari calistirir. Bastirici bean
        // aktifse (tuketici migration'i yok) bu ObjectProvider bostur, hicbir sey olmaz.
        consumerFlywayInitializer.getIfAvailable();
        return new NotificationSchemaInitializer(
                resolveDataSource(notificationDataSource, appDataSource),
                properties.getSchema(), properties.getTableName(),
                properties.getTargeting().isEnabled());
    }

    /**
     * Bu bean'in amaci: uygulamanin KENDI Flyway kurulumu YOKKEN, Spring Boot'un
     * varsayilan FlywayAutoConfiguration'inin devreye girip classpath:db/migration'i
     * (ve kutuphanenin tablolariyla dolu semayi) taramasini ve "Found non-empty
     * schema(s) but no schema history table" hatasi vermesini onlemek. Spring Boot'un
     * Flyway'i @ConditionalOnMissingBean(Flyway.class) ile korunur; biz
     * @AutoConfigureBefore(FlywayAutoConfiguration.class) oldugumuz icin once bu bean
     * kaydolur ve Spring kendi Flyway'ini hic olusturmaz.
     * <p>
     * ONEMLI: bu bean iki sekilde geri cekilir, cunku context'te bir Flyway bean'i
     * birakmak Spring Boot'un FlywayAutoConfiguration'ini (migration'lari calistiran
     * FlywayMigrationInitializer dahil) TAMAMEN devre disi birakir:
     *   - @ConditionalOnMissingBean(Flyway.class): uygulama kendi Flyway bean'ini
     *     tanimlamissa.
     *   - @Conditional(OnNoConsumerFlywayMigrations.class): uygulama Boot'un klasik
     *     yolunu kullaniyorsa (classpath:db/migration altinda .sql, ya da
     *     spring.flyway.locations ayarli). Bu kontrol olmadan, bu bean tuketicinin
     *     kendi migration'larinin SESSIZCE hic calismamasina yol acardi.
     * Her iki durumda da kutuphanenin kendi semasi hazirdir: {@link NotificationSchemaInitializer}
     * kendi (ayri gecmis tablolu) Flyway calistirmasini afterPropertiesSet()'te
     * bagimsiz yapar, bu bean'e bagli degildir.
     */
    @Bean
    @ConditionalOnProperty(prefix = "notification", name = "initialize-schema", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(Flyway.class)
    @Conditional(OnNoConsumerFlywayMigrations.class)
    public Flyway notificationFlyway(NotificationSchemaInitializer schemaInitializer) {
        return schemaInitializer.getFlyway();
    }

    /**
     * Kutuphanenin KENDI ObjectMapper'i - bilincli olarak global/@Primary DEGIL.
     * Sadece kutuphanenin ic bilesenleri (WebSocket handler, LocalBroadcaster,
     * JdbcNotificationRepository) bunu @Qualifier("notificationObjectMapper") ile
     * alir. Boylece kutuphanenin tel formati (WebSocket JSON'i) uygulamanin
     * Jackson yapilandirmasindan bagimsiz ve deterministiktir.
     * <p>
     * ONEMLI - bu bean uygulamanin global ObjectMapper'ini ELE GECIRMEZ, ama bu
     * SADECE sinif seviyesindeki @AutoConfigureAfter(JacksonAutoConfiguration.class)
     * sayesinde: Boot'un JacksonAutoConfiguration'i bizden ONCE calisir, kendi
     * @Primary ObjectMapper'ini kaydeder; bizim bean tip olarak ObjectMapper olsa
     * da context'e ikincil (non-primary) girer, MVC / @RequestBody hep Boot'un
     * dogru yapilandirilmis mapper'ini kullanir. O @AutoConfigureAfter kaldirilirsa
     * bizim bean once girer ve Boot'un jacksonObjectMapper()'i
     * @ConditionalOnMissingBean(ObjectMapper.class) ile geri cekilir - tum uygulama
     * bu ciplak mapper'a duser (bilinmeyen JSON alani -> 400, tarih ayarlari yok).
     * <p>
     * Ileri seviye ihtiyac icin ayni ADLA bir bean tanimlayan uygulama bunu
     * yine de ezebilir (@ConditionalOnMissingBean(name = ...)).
     */
    @Bean
    @ConditionalOnMissingBean(name = "notificationObjectMapper")
    public ObjectMapper notificationObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /** Kullanan uygulama kendi NotificationRepository implementasyonunu tanimlarsa, bizimki devre disi kalir. */
    @Bean
    @ConditionalOnMissingBean(NotificationRepository.class)
    public NotificationRepository notificationRepository(
            NotificationProperties properties,
            @Qualifier("notificationObjectMapper") ObjectMapper notificationObjectMapper,
            ObjectProvider<NotificationSchemaInitializer> schemaInitializer,
            @Qualifier("notificationDataSource") ObjectProvider<DataSource> notificationDataSource,
            ObjectProvider<DataSource> appDataSource) {
        // schemaInitializer sonucu kullanilmiyor gibi gorunse de onemli: varsa
        // once onu olusturur (semayi hazirlar), sonra repository'yi kurar.
        // notification.initialize-schema=false ise hic yoktur, o zaman semayi
        // hazirlamak cagiran uygulamanin sorumlulugundadir.
        schemaInitializer.getIfAvailable();
        return new JdbcNotificationRepository(
                resolveDataSource(notificationDataSource, appDataSource),
                notificationObjectMapper,
                properties.getSchema(), properties.getTableName());
    }

    /** Kullanan uygulama kendi NotificationService implementasyonunu tanimlarsa, bizimki devre disi kalir. */
    @Bean
    @ConditionalOnMissingBean(NotificationService.class)
    public NotificationService notificationService(NotificationRepository notificationRepository,
                                                      NotificationBroadcaster notificationBroadcaster,
                                                      ApplicationEventPublisher eventPublisher) {
        return new DefaultNotificationService(notificationRepository, notificationBroadcaster, eventPublisher);
    }

    /**
     * WebSocket kapatilmissa (notification.websocket.enabled=false), yayin yapan gercek bir
     * mekanizma olmadigi icin bu bos implementasyon devreye girer. WebSocket acikken kullanilan
     * gercek implementasyon (LocalBroadcaster) NotificationWebSocketAutoConfiguration icinde.
     */
    @Bean
    @ConditionalOnProperty(prefix = "notification.websocket", name = "enabled", havingValue = "false")
    @ConditionalOnMissingBean(NotificationBroadcaster.class)
    public NotificationBroadcaster noOpBroadcaster() {
        return new NoOpBroadcaster();
    }
}