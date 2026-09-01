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
package io.github.bilalefeuysl.notification.core.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bilalefeuysl.notification.core.model.Notification;
import io.github.bilalefeuysl.notification.core.model.NotificationAudience;
import io.github.bilalefeuysl.notification.core.model.NotificationPriority;
import io.github.bilalefeuysl.notification.core.model.NotificationIdentity;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class JdbcNotificationRepository implements NotificationRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final String COLUMNS =
            "id, classification_tr, message_tr, classification_en, message_en, type, priority, source_device_id, "
            + "created_at, visible, read, saved, metadata, audience_type, audience_value";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    private final String insertSql;
    private final String selectByIdSql;
    private final String selectNewestSql;
    private final String selectBeforeSql;
    private final String selectNewestByPrioritySql;
    private final String selectBeforeByPrioritySql;
    private final String hideSql;
    private final String hideAllSql;
    private final String markAsReadSql;
    private final String selectNewestForIdentitySql;
    private final String selectBeforeForIdentitySql;
    private final String selectNewestByPriorityForIdentitySql;
    private final String selectBeforeByPriorityForIdentitySql;
    private final String userStateTable;
    private final String hideForIdentitySql;
    private final String hideAllForIdentitySql;
    private final String markAsReadForIdentitySql;
    private final String countUnreadSql;
    private final String countUnreadForIdentitySql;
    private final String setSavedSql;
    private final String selectSavedNewestSql;
    private final String selectSavedBeforeSql;
    private final String setSavedForIdentitySql;
    private final String selectSavedNewestForIdentitySql;
    private final String selectSavedBeforeForIdentitySql;
    private final String searchNewestSql;
    private final String searchBeforeSql;
    private final String searchNewestForIdentitySql;
    private final String searchBeforeForIdentitySql;
    private final String sortedByPriorityFirstSql;
    private final String sortedByPriorityAfterCursorSql;
    private final String sortedByPriorityFirstForIdentitySql;
    private final String sortedByPriorityAfterCursorForIdentitySql;

    private final RowMapper<Notification> rowMapper = this::mapRow;

    public JdbcNotificationRepository(DataSource dataSource, String schema, String tableName) {
        this(new NamedParameterJdbcTemplate(dataSource), new ObjectMapper(), schema, tableName);
    }

    public JdbcNotificationRepository(DataSource dataSource, ObjectMapper objectMapper,
                                      String schema, String tableName) {
        this(new NamedParameterJdbcTemplate(dataSource), objectMapper, schema, tableName);
    }

    public JdbcNotificationRepository(NamedParameterJdbcTemplate jdbc,
                                      ObjectMapper objectMapper,
                                      String schema,
                                      String tableName) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;

        String table = SqlIdentifiers.qualify(schema, tableName);

                this.userStateTable = SqlIdentifiers.qualify(schema, "notification_user_state");

        this.insertSql = "INSERT INTO " + table + " (" + COLUMNS + ") "
                + "VALUES (:id, :classificationTr, :messageTr, :classificationEn, :messageEn, :type, :priority, :sourceDeviceId, "
                + ":createdAt, :visible, :read, :saved, CAST(:metadata AS jsonb), :audienceType, :audienceValue)";

        this.selectByIdSql = "SELECT " + COLUMNS + " FROM " + table + " WHERE id = :id";

        // Iki ayri sorgu: "before" yoksa en yeniden basla, varsa o andan eskileri getir.
        this.selectNewestSql = "SELECT " + COLUMNS + " FROM " + table
                + " WHERE visible = TRUE ORDER BY created_at DESC, id DESC LIMIT :limit";

        this.selectBeforeSql = "SELECT " + COLUMNS + " FROM " + table
                + " WHERE visible = TRUE AND created_at < :before ORDER BY created_at DESC, id DESC LIMIT :limit";

        this.selectNewestByPrioritySql = "SELECT " + COLUMNS + " FROM " + table
                + " WHERE visible = TRUE AND priority = :priority ORDER BY created_at DESC, id DESC LIMIT :limit";

        this.selectBeforeByPrioritySql = "SELECT " + COLUMNS + " FROM " + table
                + " WHERE visible = TRUE AND priority = :priority AND created_at < :before "
                + "ORDER BY created_at DESC, id DESC LIMIT :limit";

        // Hedefli bildirim sorgulari - artik kullaniciya ozel gizli/okundu
        // durumunu da hesaba katiyor: notification_user_state ile LEFT JOIN,
        // hidden_at dolu olanlar (bu kullanici gizlemis) haric tutuluyor,
        // "read" degeri artik n.read yerine (bu kullanici okumus mu) oluyor.
        String identityColumns = "n.id, n.classification_tr, n.message_tr, n.classification_en, n.message_en, "
                + "n.type, n.priority, n.source_device_id, "
                + "n.created_at, n.visible, (s.read_at IS NOT NULL) AS read, (s.saved_at IS NOT NULL) AS saved, n.metadata, "
                + "n.audience_type, n.audience_value";
        String identityJoin = " FROM " + table + " n LEFT JOIN " + userStateTable
                + " s ON s.notification_id = n.id AND s.user_id = :userId";
        String identityAudienceCondition = "(n.audience_type = 'EVERYONE' "
                + "OR (n.audience_type = 'SPECIFIC_USER' AND n.audience_value = :userId) "
                + "OR (n.audience_type = 'ROLE' AND n.audience_value IN (:roles)))";
        String identityVisibleCondition = "n.visible = TRUE AND s.hidden_at IS NULL AND " + identityAudienceCondition;

        this.selectNewestForIdentitySql = "SELECT " + identityColumns + identityJoin
                + " WHERE " + identityVisibleCondition
                + " ORDER BY n.created_at DESC, n.id DESC LIMIT :limit";

        this.selectBeforeForIdentitySql = "SELECT " + identityColumns + identityJoin
                + " WHERE " + identityVisibleCondition + " AND n.created_at < :before"
                + " ORDER BY n.created_at DESC, n.id DESC LIMIT :limit";

        this.selectNewestByPriorityForIdentitySql = "SELECT " + identityColumns + identityJoin
                + " WHERE " + identityVisibleCondition + " AND n.priority = :priority"
                + " ORDER BY n.created_at DESC, n.id DESC LIMIT :limit";

        this.selectBeforeByPriorityForIdentitySql = "SELECT " + identityColumns + identityJoin
                + " WHERE " + identityVisibleCondition + " AND n.priority = :priority AND n.created_at < :before"
                + " ORDER BY n.created_at DESC, n.id DESC LIMIT :limit";

        // Kisiye ozel gizleme/okundu isaretleme: notification_user_state'e
        // "upsert" (yoksa ekle, varsa guncelle) yapiyoruz.
        this.hideForIdentitySql = "INSERT INTO " + userStateTable + " (notification_id, user_id, hidden_at) "
                + "SELECT n.id, :userId, now() FROM " + table + " n "
                + "WHERE n.id = :id AND n.visible = TRUE AND " + identityAudienceCondition + " "
                + "ON CONFLICT (notification_id, user_id) DO UPDATE SET hidden_at = now() WHERE " + userStateTable + ".hidden_at IS NULL";

        this.hideAllForIdentitySql = "INSERT INTO " + userStateTable + " (notification_id, user_id, hidden_at) "
                + "SELECT n.id, :userId, now() FROM " + table + " n "
                + "WHERE n.visible = TRUE AND " + identityAudienceCondition + " "
                + "ON CONFLICT (notification_id, user_id) DO UPDATE SET hidden_at = now() WHERE " + userStateTable + ".hidden_at IS NULL";

        this.markAsReadForIdentitySql = "INSERT INTO " + userStateTable + " (notification_id, user_id, read_at) "
                + "SELECT n.id, :userId, now() FROM " + table + " n "
                + "WHERE n.id IN (:ids) AND n.visible = TRUE AND " + identityAudienceCondition + " "
                + "ON CONFLICT (notification_id, user_id) DO UPDATE SET read_at = now() WHERE " + userStateTable + ".read_at IS NULL";

        this.hideSql = "UPDATE " + table + " SET visible = FALSE WHERE id = :id AND visible = TRUE";
        this.hideAllSql = "UPDATE " + table + " SET visible = FALSE WHERE visible = TRUE";
        // "id IN (:ids)" - jdbc.update parametre olarak bir List verildiginde
        // NamedParameterJdbcTemplate bunu otomatik olarak SQL IN listesine cevirir.
        this.markAsReadSql = "UPDATE " + table + " SET read = TRUE WHERE id IN (:ids)";

        this.countUnreadSql = "SELECT COUNT(*) FROM " + table + " WHERE visible = TRUE AND read = FALSE";
        this.countUnreadForIdentitySql = "SELECT COUNT(*)" + identityJoin
                + " WHERE " + identityVisibleCondition + " AND s.read_at IS NULL";

        this.setSavedSql = "UPDATE " + table + " SET saved = :saved WHERE id = :id AND visible = TRUE";

        this.selectSavedNewestSql = "SELECT " + COLUMNS + " FROM " + table
                + " WHERE visible = TRUE AND saved = TRUE ORDER BY created_at DESC, id DESC LIMIT :limit";

        this.selectSavedBeforeSql = "SELECT " + COLUMNS + " FROM " + table
                + " WHERE visible = TRUE AND saved = TRUE AND created_at < :before "
                + "ORDER BY created_at DESC, id DESC LIMIT :limit";

        // read/hidden'in aksine "saved" GERI ALINABILIR - tek yonlu bir upsert
        // yetmiyor, :saved parametresine gore saved_at'i ya "now()" ya da NULL
        // yapan bir CASE gerekiyor (hem ilk ekte hem sonraki guncellemede).
        this.setSavedForIdentitySql = "INSERT INTO " + userStateTable + " (notification_id, user_id, saved_at) "
                + "SELECT n.id, :userId, CASE WHEN :saved THEN now() ELSE NULL END FROM " + table + " n "
                + "WHERE n.id = :id AND n.visible = TRUE AND " + identityAudienceCondition + " "
                + "ON CONFLICT (notification_id, user_id) DO UPDATE SET saved_at = CASE WHEN :saved THEN now() ELSE NULL END";

        this.selectSavedNewestForIdentitySql = "SELECT " + identityColumns + identityJoin
                + " WHERE " + identityVisibleCondition + " AND s.saved_at IS NOT NULL"
                + " ORDER BY n.created_at DESC, n.id DESC LIMIT :limit";

        this.selectSavedBeforeForIdentitySql = "SELECT " + identityColumns + identityJoin
                + " WHERE " + identityVisibleCondition + " AND s.saved_at IS NOT NULL AND n.created_at < :before"
                + " ORDER BY n.created_at DESC, n.id DESC LIMIT :limit";

        // Serbest metin arama: classification/message/type/source_device_id
        // ve bicimlendirilmis tarih metninin HERHANGI BIRINDE (buyuk/kucuk
        // harf duyarsiz ILIKE) :pattern gecenleri buluyor. :pattern, Java
        // tarafinda '%...%' seklinde ve ozel LIKE karakterleri (% _ \)
        // kacirilarak hazirlaniyor (bkz. buildLikePattern) - kullanicinin
        // yazdigi % veya _ harfi gercek bir joker karakter GIBI DAVRANMASIN.
        String searchCondition = "(classification_tr ILIKE :pattern ESCAPE '\\' "
                + "OR message_tr ILIKE :pattern ESCAPE '\\' "
                + "OR classification_en ILIKE :pattern ESCAPE '\\' "
                + "OR message_en ILIKE :pattern ESCAPE '\\' "
                + "OR type ILIKE :pattern ESCAPE '\\' "
                + "OR source_device_id ILIKE :pattern ESCAPE '\\' "
                + "OR to_char(created_at, 'DD.MM.YYYY HH24:MI') ILIKE :pattern ESCAPE '\\')";

        this.searchNewestSql = "SELECT " + COLUMNS + " FROM " + table
                + " WHERE visible = TRUE AND " + searchCondition
                + " ORDER BY created_at DESC, id DESC LIMIT :limit";

        this.searchBeforeSql = "SELECT " + COLUMNS + " FROM " + table
                + " WHERE visible = TRUE AND " + searchCondition + " AND created_at < :before"
                + " ORDER BY created_at DESC, id DESC LIMIT :limit";

        String identitySearchCondition = "(n.classification_tr ILIKE :pattern ESCAPE '\\' "
                + "OR n.message_tr ILIKE :pattern ESCAPE '\\' "
                + "OR n.classification_en ILIKE :pattern ESCAPE '\\' "
                + "OR n.message_en ILIKE :pattern ESCAPE '\\' "
                + "OR n.type ILIKE :pattern ESCAPE '\\' "
                + "OR n.source_device_id ILIKE :pattern ESCAPE '\\' "
                + "OR to_char(n.created_at, 'DD.MM.YYYY HH24:MI') ILIKE :pattern ESCAPE '\\')";

        this.searchNewestForIdentitySql = "SELECT " + identityColumns + identityJoin
                + " WHERE " + identityVisibleCondition + " AND " + identitySearchCondition
                + " ORDER BY n.created_at DESC, n.id DESC LIMIT :limit";

        this.searchBeforeForIdentitySql = "SELECT " + identityColumns + identityJoin
                + " WHERE " + identityVisibleCondition + " AND " + identitySearchCondition + " AND n.created_at < :before"
                + " ORDER BY n.created_at DESC, n.id DESC LIMIT :limit";

        // Opt-in oncelik sirali liste (B11) - TAMAMEN ayri bir sorgu yolu,
        // yukaridaki hicbir sorguyu etkilemez. Tek bir Instant imlec yetmez
        // (ayni created_at farkli oncelikte olabilir) - CTE'de once bir
        // "priority_rank" hesaplanip (HIGH=2, NORMAL=1, LOW=0), sayfalama
        // Postgres'in row-value karsilastirmasiyla (priority_rank, created_at, id)
        // uclusu uzerinden yapiliyor - standart "keyset pagination" deseni.
        String rankedCte = "WITH ranked AS (SELECT " + COLUMNS
                + ", CASE priority WHEN 'HIGH' THEN 2 WHEN 'NORMAL' THEN 1 ELSE 0 END AS priority_rank"
                + " FROM " + table + " WHERE visible = TRUE) ";

        this.sortedByPriorityFirstSql = rankedCte
                + "SELECT " + COLUMNS + " FROM ranked"
                + " ORDER BY priority_rank DESC, created_at DESC, id DESC LIMIT :limit";

        this.sortedByPriorityAfterCursorSql = rankedCte
                + "SELECT " + COLUMNS + " FROM ranked"
                + " WHERE (priority_rank, created_at, id) < (:cursorRank, :cursorCreatedAt, :cursorId)"
                + " ORDER BY priority_rank DESC, created_at DESC, id DESC LIMIT :limit";

        String identityRankedCte = "WITH ranked AS (SELECT " + identityColumns
                + ", CASE n.priority WHEN 'HIGH' THEN 2 WHEN 'NORMAL' THEN 1 ELSE 0 END AS priority_rank"
                + identityJoin + " WHERE " + identityVisibleCondition + ") ";

        this.sortedByPriorityFirstForIdentitySql = identityRankedCte
                + "SELECT " + COLUMNS + " FROM ranked"
                + " ORDER BY priority_rank DESC, created_at DESC, id DESC LIMIT :limit";

        this.sortedByPriorityAfterCursorForIdentitySql = identityRankedCte
                + "SELECT " + COLUMNS + " FROM ranked"
                + " WHERE (priority_rank, created_at, id) < (:cursorRank, :cursorCreatedAt, :cursorId)"
                + " ORDER BY priority_rank DESC, created_at DESC, id DESC LIMIT :limit";
    }

    @Override
    public Notification save(Notification notification) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", notification.id())
                .addValue("classificationTr", notification.classification())
                .addValue("messageTr", notification.message())
                .addValue("classificationEn", notification.classificationEn())
                .addValue("messageEn", notification.messageEn())
                .addValue("type", notification.type())
                .addValue("priority", notification.priority().name())
                .addValue("sourceDeviceId", notification.sourceDeviceId())
                .addValue("createdAt", toOffsetDateTime(notification.createdAt()))
                .addValue("visible", notification.visible())
                .addValue("read", notification.read())
                .addValue("saved", notification.saved())
                .addValue("metadata", writeMetadata(notification.metadata()))
                .addValue("audienceType", audienceType(notification.audience()))
                .addValue("audienceValue", audienceValue(notification.audience()));

        jdbc.update(insertSql, params);
        return notification;
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        List<Notification> rows = jdbc.query(selectByIdSql, new MapSqlParameterSource("id", id), rowMapper);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<Notification> findVisibleBefore(Instant before, int limit) {
        if (before == null) {
            return jdbc.query(selectNewestSql, new MapSqlParameterSource("limit", limit), rowMapper);
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("before", toOffsetDateTime(before))
                .addValue("limit", limit);
        return jdbc.query(selectBeforeSql, params, rowMapper);
    }

    @Override
    public List<Notification> findVisibleBefore(Instant before, int limit, NotificationPriority priority) {
        if (priority == null) {
            return findVisibleBefore(before, limit);
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("priority", priority.name());
        if (before == null) {
            return jdbc.query(selectNewestByPrioritySql, params, rowMapper);
        }
        params.addValue("before", toOffsetDateTime(before));
        return jdbc.query(selectBeforeByPrioritySql, params, rowMapper);
    }

    @Override
    public boolean hide(UUID id) {
        return jdbc.update(hideSql, new MapSqlParameterSource("id", id)) > 0;
    }

    @Override
    public int hideAll() {
        return jdbc.update(hideAllSql, new MapSqlParameterSource());
    }

    @Override
    public int markAsRead(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return jdbc.update(markAsReadSql, new MapSqlParameterSource("ids", ids));
    }

    @Override
    public int countUnread() {
        Integer count = jdbc.queryForObject(countUnreadSql, new MapSqlParameterSource(), Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    public boolean setSaved(UUID id, boolean saved) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id).addValue("saved", saved);
        return jdbc.update(setSavedSql, params) > 0;
    }

    @Override
    public List<Notification> findSavedBefore(Instant before, int limit) {
        if (before == null) {
            return jdbc.query(selectSavedNewestSql, new MapSqlParameterSource("limit", limit), rowMapper);
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("before", toOffsetDateTime(before))
                .addValue("limit", limit);
        return jdbc.query(selectSavedBeforeSql, params, rowMapper);
    }

    @Override
    public boolean setSavedForIdentity(UUID id, boolean saved, NotificationIdentity identity) {
        MapSqlParameterSource params = identityParams(identity).addValue("id", id).addValue("saved", saved);
        return jdbc.update(setSavedForIdentitySql, params) > 0;
    }

    @Override
    public List<Notification> findSavedForIdentity(Instant before, int limit, NotificationIdentity identity) {
        MapSqlParameterSource params = identityParams(identity).addValue("limit", limit);
        if (before == null) {
            return jdbc.query(selectSavedNewestForIdentitySql, params, rowMapper);
        }
        params.addValue("before", toOffsetDateTime(before));
        return jdbc.query(selectSavedBeforeForIdentitySql, params, rowMapper);
    }

    @Override
    public List<Notification> searchVisibleBefore(String query, Instant before, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("pattern", buildLikePattern(query))
                .addValue("limit", limit);
        if (before == null) {
            return jdbc.query(searchNewestSql, params, rowMapper);
        }
        params.addValue("before", toOffsetDateTime(before));
        return jdbc.query(searchBeforeSql, params, rowMapper);
    }

    @Override
    public List<Notification> searchVisibleForIdentity(String query, Instant before, int limit,
                                                         NotificationIdentity identity) {
        MapSqlParameterSource params = identityParams(identity)
                .addValue("pattern", buildLikePattern(query))
                .addValue("limit", limit);
        if (before == null) {
            return jdbc.query(searchNewestForIdentitySql, params, rowMapper);
        }
        params.addValue("before", toOffsetDateTime(before));
        return jdbc.query(searchBeforeForIdentitySql, params, rowMapper);
    }

    /**
     * Kullanicinin yazdigi serbest metni bir ILIKE deseni haline getirir -
     * SQL LIKE'ta ozel anlami olan % / _ / \ karakterlerini kacirip (\%, \_,
     * \\) baslarina/sonlarina % ekliyor. Boylece kullanici "%50" gibi bir
     * sey yazsa bile bu joker karakter GIBI DAVRANMAZ, harfi harfine aranir.
     */
    private static String buildLikePattern(String query) {
        String escaped = query
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    @Override
    public List<Notification> findVisibleForIdentity(Instant before, int limit, NotificationIdentity identity) {
        MapSqlParameterSource params = identityParams(identity).addValue("limit", limit);
        if (before == null) {
            return jdbc.query(selectNewestForIdentitySql, params, rowMapper);
        }
        params.addValue("before", toOffsetDateTime(before));
        return jdbc.query(selectBeforeForIdentitySql, params, rowMapper);
    }

    @Override
    public List<Notification> findVisibleForIdentity(Instant before, int limit, NotificationPriority priority,
                                                       NotificationIdentity identity) {
        if (priority == null) {
            return findVisibleForIdentity(before, limit, identity);
        }
        MapSqlParameterSource params = identityParams(identity)
                .addValue("limit", limit)
                .addValue("priority", priority.name());
        if (before == null) {
            return jdbc.query(selectNewestByPriorityForIdentitySql, params, rowMapper);
        }
        params.addValue("before", toOffsetDateTime(before));
        return jdbc.query(selectBeforeByPriorityForIdentitySql, params, rowMapper);
    }

    @Override
    public List<Notification> findVisibleSortedByPriority(NotificationPriority cursorPriority, Instant cursorCreatedAt,
                                                            UUID cursorId, int limit) {
        if (cursorCreatedAt == null) {
            return jdbc.query(sortedByPriorityFirstSql, new MapSqlParameterSource("limit", limit), rowMapper);
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("cursorRank", priorityRank(cursorPriority))
                .addValue("cursorCreatedAt", toOffsetDateTime(cursorCreatedAt))
                .addValue("cursorId", cursorId);
        return jdbc.query(sortedByPriorityAfterCursorSql, params, rowMapper);
    }

    @Override
    public List<Notification> findVisibleSortedByPriorityForIdentity(NotificationPriority cursorPriority,
            Instant cursorCreatedAt, UUID cursorId, int limit, NotificationIdentity identity) {
        MapSqlParameterSource params = identityParams(identity).addValue("limit", limit);
        if (cursorCreatedAt == null) {
            return jdbc.query(sortedByPriorityFirstForIdentitySql, params, rowMapper);
        }
        params.addValue("cursorRank", priorityRank(cursorPriority))
                .addValue("cursorCreatedAt", toOffsetDateTime(cursorCreatedAt))
                .addValue("cursorId", cursorId);
        return jdbc.query(sortedByPriorityAfterCursorForIdentitySql, params, rowMapper);
    }

    /** ranked CTE'deki CASE ifadesiyle BIREBIR ayni eslesme - imlec karsilastirmasi bu ranga gore yapilir. */
    private static int priorityRank(NotificationPriority priority) {
        return switch (priority) {
            case HIGH -> 2;
            case NORMAL -> 1;
            case LOW -> 0;
        };
    }

        @Override
    public boolean hideForIdentity(UUID id, NotificationIdentity identity) {
        MapSqlParameterSource params = identityParams(identity).addValue("id", id);
        return jdbc.update(hideForIdentitySql, params) > 0;
    }

    @Override
    public int hideAllForIdentity(NotificationIdentity identity) {
        return jdbc.update(hideAllForIdentitySql, identityParams(identity));
    }

    @Override
    public int markAsReadForIdentity(List<UUID> ids, NotificationIdentity identity) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        MapSqlParameterSource params = identityParams(identity).addValue("ids", ids);
        return jdbc.update(markAsReadForIdentitySql, params);
    }

    @Override
    public int countUnreadForIdentity(NotificationIdentity identity) {
        Integer count = jdbc.queryForObject(countUnreadForIdentitySql, identityParams(identity), Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * userId/roles parametrelerini hazirlar. roles bos ise "IN (:roles)" bos
     * koleksiyonla calismadigi (Spring/JDBC hata firlatir) icin gercek bir
     * role adiyla asla eslesmeyecek bir yer tutucu deger kullanilir - bu,
     * "hicbir role dahil degil" davranisini guvenli sekilde simule eder.
     */
    private MapSqlParameterSource identityParams(NotificationIdentity identity) {
        List<String> roles = identity.roles().isEmpty()
                ? List.of("__no_roles__")
                : List.copyOf(identity.roles());
        return new MapSqlParameterSource()
                .addValue("userId", identity.userId())
                .addValue("roles", roles);
    }

    private Notification mapRow(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
        return new Notification(
                rs.getObject("id", UUID.class),
                rs.getString("classification_tr"),
                rs.getString("message_tr"),
                rs.getString("classification_en"),
                rs.getString("message_en"),
                rs.getString("type"),
                NotificationPriority.valueOf(rs.getString("priority")),
                rs.getString("source_device_id"),
                createdAt == null ? null : createdAt.toInstant(),
                rs.getBoolean("visible"),
                rs.getBoolean("read"),
                rs.getBoolean("saved"),
                readMetadata(rs.getString("metadata")),
                readAudience(rs.getString("audience_type"), rs.getString("audience_value")));
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private String writeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception ex) {
            throw new IllegalArgumentException("metadata JSON'a cevrilemedi", ex);
        }
    }

    private Map<String, Object> readMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalStateException("metadata JSON'dan okunamadi: " + json, ex);
        }
    }

        /** NotificationAudience -> veritabanina yazilacak "tip" metni. */
    private String audienceType(NotificationAudience audience) {
        if (audience instanceof NotificationAudience.SpecificUser) return "SPECIFIC_USER";
        if (audience instanceof NotificationAudience.Role) return "ROLE";
        return "EVERYONE";
    }

    /** NotificationAudience -> veritabanina yazilacak "deger" (userId veya roleName). Everyone icin null. */
    private String audienceValue(NotificationAudience audience) {
        if (audience instanceof NotificationAudience.SpecificUser su) return su.userId();
        if (audience instanceof NotificationAudience.Role role) return role.roleName();
        return null;
    }

    /** Veritabanindan okunan tip+deger -> NotificationAudience nesnesi. */
    private NotificationAudience readAudience(String type, String value) {
        if (type == null || "EVERYONE".equals(type)) {
            return new NotificationAudience.Everyone();
        }
        if ("SPECIFIC_USER".equals(type)) {
            return new NotificationAudience.SpecificUser(value);
        }
        if ("ROLE".equals(type)) {
            return new NotificationAudience.Role(value);
        }
        throw new IllegalStateException("Bilinmeyen audience_type: " + type);
    }
}
