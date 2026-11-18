package com.wherelee.edu.modules.platform.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordGeneratorTest {

    @Test
    void generatedPasswordSatisfiesStrongPolicy() {
        for (int i = 0; i < 200; i++) {
            String password = PasswordGenerator.generate();
            assertThat(password).hasSize(16);
            assertThat(password.chars().anyMatch(Character::isLetter)).isTrue();
            assertThat(password.chars().anyMatch(Character::isDigit)).isTrue();
            assertThat(password).doesNotContain("I", "O", "l", "o", "0", "1");
        }
    }

    @Test
    void generatedPasswordsAreRandom() {
        assertThat(PasswordGenerator.generate()).isNotEqualTo(PasswordGenerator.generate());
    }
}
