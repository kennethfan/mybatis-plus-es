package io.github.kennethfan.mpes.support;

/**
 * 适配层统一运行时异常：所有 ES IO 异常与语义错误均以 unchecked 形态抛出。
 */
public class EsOpsException extends RuntimeException {

    public EsOpsException(String message) {
        super(message);
    }

    public EsOpsException(String message, Throwable cause) {
        super(message, cause);
    }
}
