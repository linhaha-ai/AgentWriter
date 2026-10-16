package com.agentwriter.service.event;

import com.agentwriter.dto.ScheduleRuntimeConfig;

import java.util.Objects;

public record ScheduleConfigurationChangedEvent(ScheduleRuntimeConfig config) {

    public ScheduleConfigurationChangedEvent {
        Objects.requireNonNull(config, "调度运行配置不能为空");
    }
}
