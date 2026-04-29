package com.suannai.netdisk.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AppSettingsService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AppSettingsService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isEnabled(String key) {
        List<String> values = jdbcTemplate.query(
                "SELECT setting_value FROM app_settings WHERE setting_key = :key",
                new MapSqlParameterSource("key", key),
                (resultSet, rowNum) -> resultSet.getString("setting_value")
        );
        return !values.isEmpty() && "YES".equalsIgnoreCase(values.get(0));
    }
}
