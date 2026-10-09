package io.github.kennethfan.mpes.support;

import io.github.kennethfan.mpes.core.SFunction;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;

/**
 * 从 Lambda 表达式中提取属性名的工具（MP LambdaUtils 的轻量替代，不依赖 mybatis-plus 内核）。
 */
public final class LambdaUtils {

    private LambdaUtils() {
    }

    /**
     * 解析 {@code Product::getName} 形态的 Lambda 为属性名 "productName"。
     * 仅支持标准 getter 引用（getXxx / isXxx）。
     */
    public static <T> String propertyName(SFunction<T, ?> fn) {
        SerializedLambda lambda = serialized(fn);
        String methodName = lambda.getImplMethodName();

        if (methodName.startsWith("get") && methodName.length() > 3) {
            return decapitalize(methodName.substring(3));
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return decapitalize(methodName.substring(2));
        }
        throw new EsOpsException(
                "无法从 Lambda 推导属性名: " + methodName + "，仅支持标准 getter 方法引用（getXxx / isXxx）");
    }

    private static SerializedLambda serialized(SFunction<?, ?> fn) {
        try {
            Method writeReplace = fn.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            return (SerializedLambda) writeReplace.invoke(fn);
        } catch (ReflectiveOperationException e) {
            throw new EsOpsException("Lambda 序列化失败，无法提取字段引用", e);
        }
    }

    private static String decapitalize(String name) {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
