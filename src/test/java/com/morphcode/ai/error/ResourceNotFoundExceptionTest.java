package com.morphcode.ai.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceNotFoundExceptionTest {

    @Test
    void constructor_setsResourceNameAndId() {
        var ex = new ResourceNotFoundException("Project", "42");
        assertThat(ex.getResourceName()).isEqualTo("Project");
        assertThat(ex.getResourceId()).isEqualTo("42");
    }

    @Test
    void message_containsResourceNameAndId() {
        var ex = new ResourceNotFoundException("User", "99");
        assertThat(ex.getMessage()).contains("User").contains("99");
    }

    @Test
    void isRuntimeException() {
        assertThat(new ResourceNotFoundException("A", "B")).isInstanceOf(RuntimeException.class);
    }
}
