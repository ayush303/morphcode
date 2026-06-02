package com.morphcode.ai.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BadRequestExceptionTest {

    @Test
    void constructor_setsMessage() {
        var ex = new BadRequestException("something went wrong");
        assertThat(ex.getMessage()).isEqualTo("something went wrong");
    }

    @Test
    void isRuntimeException() {
        assertThat(new BadRequestException("x")).isInstanceOf(RuntimeException.class);
    }
}
