package me.codeleep.victor.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.codeleep.victor.web.config.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 接口测试基类
 * 提供MockMvc实例和认证辅助方法
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class BaseApiTest {

    protected static final Long TEST_USER_ID = 1L;
    protected static final String TEST_USERNAME = "testuser";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JwtUtils jwtUtils;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected String userToken;

    @BeforeEach
    void setUp() {
        // 确保测试用户存在（用于外键约束）
        jdbcTemplate.update(
                "INSERT INTO \"user\" (id, username, email, password_hash, nickname, status) " +
                "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING",
                TEST_USER_ID, TEST_USERNAME, "test@example.com", "hashed_password", "测试用户", "ACTIVE"
        );
        userToken = jwtUtils.generateToken(TEST_USER_ID, TEST_USERNAME);
        doSetUp();
    }

    /**
     * 子类可重写，执行额外的初始化
     */
    protected void doSetUp() {
    }

    /**
     * 为请求添加认证Header
     */
    protected MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + userToken);
    }
}
