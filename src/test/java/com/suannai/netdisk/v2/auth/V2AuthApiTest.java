package com.suannai.netdisk.v2.auth;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.session.SaSession;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class V2AuthApiTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("netdisk_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("netdisk.storage.upload-path", () -> Paths.get("target", "test-uploads").toAbsolutePath().toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SaTokenDao saTokenDao;

    @Test
    void registerThenFetchSaTokenProfileAndLogout() throws Exception {
        String username = "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String payload = "{"
                + "\"username\":\"" + username + "\","
                + "\"password\":\"secret123\","
                + "\"nickname\":\"Demo\""
                + "}";

        Cookie tokenCookie = mockMvc.perform(post("/api/v2/auth/register")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andReturn()
                .getResponse()
                .getCookie("NETDISK_TOKEN");

        assertNotNull(tokenCookie);
        assertFalse(tokenCookie.getValue().isBlank());

        mockMvc.perform(get("/api/v2/me").cookie(tokenCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.username").value(username));

        mockMvc.perform(post("/api/v2/auth/logout").cookie(tokenCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        mockMvc.perform(get("/api/v2/me").cookie(tokenCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("\u8bf7\u5148\u767b\u5f55"));
    }

    @Test
    void jdbcSaTokenDaoStoresTimeoutsSessionsAndSearchesKeys() {
        String prefix = "test:" + UUID.randomUUID() + ":";
        String alpha = prefix + "alpha";
        String beta = prefix + "beta";
        String skipped = "other:" + UUID.randomUUID();
        String sessionKey = prefix + "session";

        try {
            saTokenDao.set(alpha, "A", SaTokenDao.NEVER_EXPIRE);
            saTokenDao.set(beta, "B", SaTokenDao.NEVER_EXPIRE);
            saTokenDao.set(skipped, "C", SaTokenDao.NEVER_EXPIRE);

            assertEquals("A", saTokenDao.get(alpha));
            assertEquals(SaTokenDao.NEVER_EXPIRE, saTokenDao.getTimeout(alpha));
            assertEquals(List.of(alpha, beta), saTokenDao.searchData(prefix, "", 0, -1, true));
            assertEquals(List.of(beta), saTokenDao.searchData(prefix, "beta", 0, 1, true));

            saTokenDao.update(alpha, "A2");
            assertEquals("A2", saTokenDao.get(alpha));

            saTokenDao.set(prefix + "expired", "gone", 0);
            assertNull(saTokenDao.get(prefix + "expired"));
            assertEquals(SaTokenDao.NOT_VALUE_EXPIRE, saTokenDao.getTimeout(prefix + "expired"));

            SaSession session = new SaSession(sessionKey);
            session.setLoginId(42L);
            saTokenDao.setSession(session, SaTokenDao.NEVER_EXPIRE);

            SaSession loaded = saTokenDao.getSession(sessionKey);
            assertNotNull(loaded);
            assertEquals(42L, ((Number) loaded.getLoginId()).longValue());

            saTokenDao.delete(alpha);
            assertNull(saTokenDao.get(alpha));
            assertTrue(saTokenDao.searchData(prefix, "alpha", 0, -1, true).isEmpty());
        } finally {
            saTokenDao.delete(alpha);
            saTokenDao.delete(beta);
            saTokenDao.delete(skipped);
            saTokenDao.delete(prefix + "expired");
            saTokenDao.deleteSession(sessionKey);
        }
    }
}
