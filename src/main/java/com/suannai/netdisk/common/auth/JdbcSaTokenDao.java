package com.suannai.netdisk.common.auth;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.session.SaSession;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Comparator;
import java.util.List;

@Component
public class JdbcSaTokenDao implements SaTokenDao {
    private static final long NEVER_EXPIRES_AT = -1L;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcSaTokenDao(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String get(String key) {
        Object value = getObject(key);
        return value instanceof String stringValue ? stringValue : null;
    }

    @Override
    public void set(String key, String value, long timeout) {
        setObject(key, value, timeout);
    }

    @Override
    public void update(String key, String value) {
        updateObject(key, value);
    }

    @Override
    public void delete(String key) {
        deleteObject(key);
    }

    @Override
    public long getTimeout(String key) {
        return getObjectTimeout(key);
    }

    @Override
    public void updateTimeout(String key, long timeout) {
        updateObjectTimeout(key, timeout);
    }

    @Override
    public Object getObject(String key) {
        StoredValue storedValue = findStoredValue(key);
        return storedValue == null ? null : deserialize(storedValue.valueBlob());
    }

    @Override
    public <T> T getObject(String key, Class<T> classType) {
        Object value = getObject(key);
        return value == null ? null : classType.cast(value);
    }

    @Override
    public void setObject(String key, Object object, long timeout) {
        if (timeout == 0 || timeout <= NOT_VALUE_EXPIRE) {
            deleteObject(key);
            return;
        }

        jdbcTemplate.update("""
                        INSERT INTO sa_token_store(cache_key, value_blob, expires_at)
                        VALUES(:key, :value, :expiresAt)
                        ON DUPLICATE KEY UPDATE
                            value_blob = VALUES(value_blob),
                            expires_at = VALUES(expires_at),
                            updated_at = CURRENT_TIMESTAMP
                        """,
                new MapSqlParameterSource()
                        .addValue("key", key)
                        .addValue("value", serialize(object))
                        .addValue("expiresAt", toExpiresAt(timeout))
        );
    }

    @Override
    public void updateObject(String key, Object object) {
        cleanupKeyIfExpired(key);
        jdbcTemplate.update("""
                        UPDATE sa_token_store
                        SET value_blob = :value, updated_at = CURRENT_TIMESTAMP
                        WHERE cache_key = :key
                        """,
                new MapSqlParameterSource()
                        .addValue("key", key)
                        .addValue("value", serialize(object))
        );
    }

    @Override
    public void deleteObject(String key) {
        jdbcTemplate.update(
                "DELETE FROM sa_token_store WHERE cache_key = :key",
                new MapSqlParameterSource("key", key)
        );
    }

    @Override
    public long getObjectTimeout(String key) {
        List<Long> expiresAtValues = jdbcTemplate.query(
                "SELECT expires_at FROM sa_token_store WHERE cache_key = :key",
                new MapSqlParameterSource("key", key),
                (resultSet, rowNum) -> resultSet.getLong("expires_at")
        );
        if (expiresAtValues.isEmpty()) {
            return NOT_VALUE_EXPIRE;
        }

        long expiresAt = expiresAtValues.get(0);
        if (expiresAt == NEVER_EXPIRES_AT) {
            return NEVER_EXPIRE;
        }

        long remainingMillis = expiresAt - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            deleteObject(key);
            return NOT_VALUE_EXPIRE;
        }
        return Math.max(1L, (remainingMillis + 999L) / 1000L);
    }

    @Override
    public void updateObjectTimeout(String key, long timeout) {
        if (timeout == 0 || timeout <= NOT_VALUE_EXPIRE) {
            deleteObject(key);
            return;
        }

        cleanupKeyIfExpired(key);
        jdbcTemplate.update("""
                        UPDATE sa_token_store
                        SET expires_at = :expiresAt, updated_at = CURRENT_TIMESTAMP
                        WHERE cache_key = :key
                        """,
                new MapSqlParameterSource()
                        .addValue("key", key)
                        .addValue("expiresAt", toExpiresAt(timeout))
        );
    }

    @Override
    public SaSession getSession(String sessionId) {
        return getObject(sessionId, SaSession.class);
    }

    @Override
    public void setSession(SaSession session, long timeout) {
        setObject(session.getId(), session, timeout);
    }

    @Override
    public void updateSession(SaSession session) {
        updateObject(session.getId(), session);
    }

    @Override
    public void deleteSession(String sessionId) {
        deleteObject(sessionId);
    }

    @Override
    public long getSessionTimeout(String sessionId) {
        return getObjectTimeout(sessionId);
    }

    @Override
    public void updateSessionTimeout(String sessionId, long timeout) {
        updateObjectTimeout(sessionId, timeout);
    }

    @Override
    public List<String> searchData(String prefix, String keyword, int start, int size, boolean sortType) {
        cleanupExpired();

        Comparator<String> comparator = sortType ? Comparator.naturalOrder() : Comparator.reverseOrder();
        List<String> values = jdbcTemplate.query(
                        "SELECT cache_key FROM sa_token_store WHERE expires_at = -1 OR expires_at > :now",
                        new MapSqlParameterSource("now", System.currentTimeMillis()),
                        (resultSet, rowNum) -> resultSet.getString("cache_key")
                )
                .stream()
                .filter(key -> key.startsWith(prefix))
                .filter(key -> keyword == null || keyword.isEmpty() || key.contains(keyword))
                .sorted(comparator)
                .toList();

        if (start >= values.size()) {
            return List.of();
        }
        int fromIndex = Math.max(start, 0);
        int toIndex = size < 0 ? values.size() : Math.min(values.size(), fromIndex + size);
        return values.subList(fromIndex, toIndex);
    }

    private StoredValue findStoredValue(String key) {
        List<StoredValue> values = jdbcTemplate.query(
                "SELECT value_blob, expires_at FROM sa_token_store WHERE cache_key = :key",
                new MapSqlParameterSource("key", key),
                (resultSet, rowNum) -> new StoredValue(
                        resultSet.getBytes("value_blob"),
                        resultSet.getLong("expires_at")
                )
        );
        if (values.isEmpty()) {
            return null;
        }

        StoredValue value = values.get(0);
        if (isExpired(value.expiresAt())) {
            deleteObject(key);
            return null;
        }
        return value;
    }

    private void cleanupKeyIfExpired(String key) {
        jdbcTemplate.update(
                "DELETE FROM sa_token_store WHERE cache_key = :key AND expires_at <> -1 AND expires_at <= :now",
                new MapSqlParameterSource()
                        .addValue("key", key)
                        .addValue("now", System.currentTimeMillis())
        );
    }

    private void cleanupExpired() {
        jdbcTemplate.update(
                "DELETE FROM sa_token_store WHERE expires_at <> -1 AND expires_at <= :now",
                new MapSqlParameterSource("now", System.currentTimeMillis())
        );
    }

    private boolean isExpired(long expiresAt) {
        return expiresAt != NEVER_EXPIRES_AT && expiresAt <= System.currentTimeMillis();
    }

    private long toExpiresAt(long timeout) {
        if (timeout == NEVER_EXPIRE) {
            return NEVER_EXPIRES_AT;
        }

        long millis;
        try {
            millis = Math.multiplyExact(timeout, 1000L);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }

        long now = System.currentTimeMillis();
        return Long.MAX_VALUE - now < millis ? Long.MAX_VALUE : now + millis;
    }

    private byte[] serialize(Object object) {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)) {
            objectStream.writeObject(object);
            return byteStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to serialize Sa-Token value", exception);
        }
    }

    private Object deserialize(byte[] bytes) {
        try (ObjectInputStream objectStream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return objectStream.readObject();
        } catch (IOException | ClassNotFoundException exception) {
            throw new IllegalStateException("Unable to deserialize Sa-Token value", exception);
        }
    }

    private record StoredValue(byte[] valueBlob, long expiresAt) {
    }
}
