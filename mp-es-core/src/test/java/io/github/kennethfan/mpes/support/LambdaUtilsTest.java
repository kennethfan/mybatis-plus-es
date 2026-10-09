package io.github.kennethfan.mpes.support;

import io.github.kennethfan.mpes.core.SFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Lambda → 属性名解析的单元测试（不依赖 ES）。
 */
class LambdaUtilsTest {

    @SuppressWarnings("unused")
    static class Fixture {
        private String productName;
        private boolean active;

        public String getProductName() {
            return productName;
        }

        public boolean isActive() {
            return active;
        }
    }

    @Test
    void shouldResolveGetterLambda() {
        SFunction<Fixture, String> fn = Fixture::getProductName;
        assertEquals("productName", LambdaUtils.propertyName(fn));
    }

    @Test
    void shouldResolveBooleanIsGetter() {
        SFunction<Fixture, Boolean> fn = Fixture::isActive;
        assertEquals("active", LambdaUtils.propertyName(fn));
    }

    @Test
    void shouldRejectNonGetterMethod() {
        SFunction<Fixture, String> fn = Fixture::toString;
        assertThrows(EsOpsException.class, () -> LambdaUtils.propertyName(fn));
    }
}
