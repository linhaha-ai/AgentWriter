package com.agentwriter.service;

import com.agentwriter.exception.LLMUnavailableException;
import com.agentwriter.service.impl.DefaultLLMService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultLLMServiceTest {

    @Test
    void testFixtureMustNeverReturnPlaceholderSuccess() {
        DefaultLLMService service = new DefaultLLMService();

        assertThatThrownBy(() -> service.generate("system prompt", "user message"))
                .isInstanceOf(LLMUnavailableException.class);
    }
}
