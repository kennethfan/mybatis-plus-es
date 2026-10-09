package io.github.kennethfan.mpes.metadata;

import io.github.kennethfan.mpes.annotation.EsText;
import io.github.kennethfan.mpes.support.EsOpsException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 类型推导与实体元数据解析的单元测试（不依赖 ES）。
 */
class EsTypeResolverTest {

    @SuppressWarnings("unused")
    static class Fixture {
        private String keywordField;
        @EsText(analyzer = "ik_max_word")
        private String textField;
        private Long longField;
        private Integer intField;
        private BigDecimal decimalField;
        private Boolean boolField;
        private LocalDate dateField;
        private List<String> unsupported;
    }

    private static Class<?> type(String field) throws NoSuchFieldException {
        return Fixture.class.getDeclaredField(field).getType();
    }

    private static EsText esText(String field) throws NoSuchFieldException {
        Field f = Fixture.class.getDeclaredField(field);
        return f.getAnnotation(EsText.class);
    }

    @Test
    void shouldResolveBasicTypes() throws Exception {
        assertEquals("keyword", EsTypeResolver.resolve(type("keywordField"), null));
        assertEquals("long", EsTypeResolver.resolve(type("longField"), null));
        assertEquals("integer", EsTypeResolver.resolve(type("intField"), null));
        assertEquals("double", EsTypeResolver.resolve(type("decimalField"), null));
        assertEquals("boolean", EsTypeResolver.resolve(type("boolField"), null));
        assertEquals("date", EsTypeResolver.resolve(type("dateField"), null));
    }

    @Test
    void esTextAnnotationShouldForceTextType() throws Exception {
        assertEquals("text", EsTypeResolver.resolve(type("textField"), esText("textField")));
        assertEquals("ik_max_word", esText("textField").analyzer());
    }

    @Test
    void shouldRejectUnsupportedType() {
        assertThrows(EsOpsException.class, () -> EsTypeResolver.resolve(List.class, null));
    }
}
