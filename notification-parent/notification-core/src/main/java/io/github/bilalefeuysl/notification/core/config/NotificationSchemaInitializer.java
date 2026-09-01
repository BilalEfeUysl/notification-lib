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

import io.github.bilalefeuysl.notification.core.repository.SqlIdentifiers;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import javax.sql.DataSource;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.sql.Connection;


/**
 * Bildirim tablosunu kutuphane kendisi olusturur.
 *
 * Onemli: kendi migration'larimiz AYRI bir klasorde ve AYRI bir gecmis
 * tablosunda tutulur. Boylece kutuphaneyi kullanan uygulamanin kendi
 * Flyway kurulumu varsa ikisi birbirine karismaz.
 *
 * targeting acikken, notification-targeting klasoru AYRI bir Flyway
 * calistirmasi (kendi gecmis tablosuyla: notification_targeting_schema_history)
 * ile uygulanir - AYNI Flyway'de birlestirilmez. Sebebi: iki klasorde de
 * bagimsiz V1'den baslayan numaralandirma var; birlestirilirse Flyway
 * "iki tane V1 var" diye hata verir. Ayri gecmis tablolari, iki numaralandirma
 * dizisinin birbirinden tamamen habersiz olmasini saglar.
 *
 * Ana Flyway nesnesi getFlyway() ile disariya (starter modulundeki bir
 * @Bean'e) acilir. Sebebi klasor ayrimindan farkli: Spring Boot'un kendi
 * FlywayAutoConfiguration'i @ConditionalOnMissingBean(Flyway.class) ile
 * korunur - yani context'te zaten bir Flyway bean'i varsa Spring kendi
 * varsayilan Flyway'ini hic olusturmaz. O @Bean, tuketicinin KENDI Flyway
 * migration'lari YOKKEN kaydedilir ve Spring'in Flyway'ini bastirarak "Found
 * non-empty schema(s) but no schema history table" hatasini onler. Tuketici
 * Flyway kullaniyorsa o @Bean kaydedilmez, Spring'in Flyway'i onlarin
 * migration'larini normal calistirir; bu sinif kendi semasini yine bagimsiz
 * hazirlar (bkz. starter modulu OnNoConsumerFlywayMigrations).
 */
public class NotificationSchemaInitializer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(NotificationSchemaInitializer.class);

    // ONEMLI: bu yollar bilerek "db/migration" ALTINDA DEGIL. Spring Boot'un
    // kendi Flyway'i varsayilan olarak classpath:db/migration'i ALT KLASORLERIYLE
    // BIRLIKTE tarar; kutuphanenin migration'lari orada olsaydi, kullanan
    // uygulamanin Flyway'i bizim iki ayri numaralandirma dizimizi tek liste
    // sanip "Found more than one migration with version 1" hatasiyla ACILISTA
    // COKERDI. Bu yol sayesinde kutuphanenin migration'lari kullanan
    // uygulamanin Flyway'ine tamamen gorunmezdir.
    private static final String MIGRATION_LOCATION = "classpath:db/notification-migration/core";
    private static final String TARGETING_MIGRATION_LOCATION = "classpath:db/notification-migration/targeting";
    private static final String HISTORY_TABLE = "notification_schema_history";
    private static final String TARGETING_HISTORY_TABLE = "notification_targeting_schema_history";

    private final DataSource dataSource;
    private final String schema;
    private final String tableName;
    private final boolean targetingEnabled;
    private Flyway flyway;

    public NotificationSchemaInitializer(DataSource dataSource, String schema, String tableName) {
        this(dataSource, schema, tableName, false);
    }

    public NotificationSchemaInitializer(DataSource dataSource, String schema, String tableName,
                                          boolean targetingEnabled) {
        this.dataSource = dataSource;
        this.schema = SqlIdentifiers.requireSafe(schema, "notification.schema");
        this.tableName = SqlIdentifiers.requireSafe(tableName, "notification.table-name");
        this.targetingEnabled = targetingEnabled;
    }

    @Override
    public void afterPropertiesSet() {
        migrate();
    }

    /**
     * Ana migration'i calistiran Flyway nesnesi. Spring Boot'un kendi
     * FlywayAutoConfiguration'inin @ConditionalOnMissingBean(Flyway.class)
     * korumasindan geri cekilmesini saglamak icin starter modulunde bir
     * @Bean olarak disariya acilir - bkz. sinif javadoc'u.
     */
    public Flyway getFlyway() {
        return flyway;
    }

    public void migrate() {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("notificationSchema", schema);
        placeholders.put("notificationTable", tableName);

        flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(MIGRATION_LOCATION)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .table(HISTORY_TABLE)
                .placeholders(placeholders)
                .baselineOnMigrate(true)
                // ONEMLI: baseline sürümü 0 olmali. Varsayilan (1) ile, mevcut
                // (dolu) bir veritabaninda ilk calisma V1'i "zaten uygulanmis"
                // sayip ATLAR - tablo hic olusmaz ve HICBIR HATA VERILMEZ.
                // Sorun ancak calisma aninda "relation does not exist" olarak
                // ortaya cikar. 0 ile baseline atilinca V1'den itibaren hepsi
                // calisir; migration'lar IF NOT EXISTS kullandigi icin tablo
                // zaten varsa da guvenlidir.
                .baselineVersion("0")
                .load();
        flyway.migrate();

        if (targetingEnabled) {
            Flyway targetingFlyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations(TARGETING_MIGRATION_LOCATION)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .createSchemas(true)
                    .table(TARGETING_HISTORY_TABLE)
                    .placeholders(placeholders)
                    .baselineOnMigrate(true)
                    .baselineVersion("0") // bkz. yukaridaki aciklama
                    .load();
            targetingFlyway.migrate();
        }

        if (targetingEnabled) {
            verifyTargetingSchema();
        }

        log.info("Bildirim tablosu hazir: {}.{} (hedefleme: {})", schema, tableName, targetingEnabled);
    }

    /**
     * targeting acikken notification_user_state tablosunun GERCEKTEN var
     * oldugunu dogrular. Migration sessizce atlanmis olabilir (bkz. baseline
     * aciklamasi); boyle bir durumda uygulamanin sorunsuz acilip ilk istekte
     * anlamsiz bir hata vermesindense, ACILISTA net bir mesajla durmasi
     * cok daha iyidir.
     */
    private void verifyTargetingSchema() {
        String sql = "SELECT 1 FROM information_schema.tables "
                + "WHERE table_schema = ? AND table_name = 'notification_user_state'";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException(
                            "notification.targeting.enabled=true ama '" + schema
                                    + ".notification_user_state' tablosu bulunamadi. "
                                    + "Hedefleme migration'i uygulanmamis olabilir. Kontrol edin: "
                                    + TARGETING_HISTORY_TABLE + " tablosunda sadece bir 'Flyway Baseline' "
                                    + "kaydi varsa, o kaydi silip uygulamayi yeniden baslatin.");
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Hedefleme tablosu dogrulanamadi: " + schema + ".notification_user_state", ex);
        }
    }
}