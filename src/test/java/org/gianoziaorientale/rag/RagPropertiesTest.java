package org.gianoziaorientale.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

class RagPropertiesTest {

    @Test
    void bindsDisabledRagAndOperatorSelectedAnswerModelConfiguration() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("agentsmith.rag.enabled", "true")
                .withProperty("agentsmith.rag.answer-model-id", "gemini:example-model");

        RagProperties properties = Binder.get(environment)
                .bind("agentsmith.rag", Bindable.of(RagProperties.class))
                .orElseThrow(IllegalStateException::new);

        assertTrue(properties.isEnabled());
        assertEquals("gemini:example-model", properties.getAnswerModelId());
    }

    @Test
    void ragIsDisabledByDefaultAndUsesTheFiveThousandByteThreshold() {
        RagProperties properties = new RagProperties();

        assertFalse(properties.isEnabled());
        assertEquals(5_000L, RagProperties.SMALL_FILE_MAX_BYTES);
        assertEquals("", properties.getAnswerModelId());
    }
}
