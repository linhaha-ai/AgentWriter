package com.agentwriter.exception;

import java.io.Serial;

public class SystemConfigMalformedException extends IllegalStateException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SystemConfigMalformedException(String configKey, Throwable cause) {
        super("存储的系统配置格式无效: " + configKey, cause);
    }
}