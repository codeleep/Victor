package me.codeleep.victor.core;

import me.codeleep.victor.common.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockitoAnnotations;

/**
 * Service层单元测试基类
 * 使用Mockito模拟依赖，验证业务逻辑
 */
public abstract class BaseServiceTest {

    protected static final Long TEST_USER_ID = 1L;
    protected static final String TEST_USERNAME = "testuser";

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        UserContext.setUserId(TEST_USER_ID);
        UserContext.setUsername(TEST_USERNAME);
        doSetUp();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        try {
            closeable.close();
        } catch (Exception e) {
            // ignore
        }
        doTearDown();
    }

    /**
     * 子类可重写，执行额外的初始化
     */
    protected void doSetUp() {
    }

    /**
     * 子类可重写，执行额外的清理
     */
    protected void doTearDown() {
    }
}
