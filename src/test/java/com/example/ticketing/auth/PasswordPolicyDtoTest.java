package com.example.ticketing.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class PasswordPolicyDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void forcedNewPasswordNeedsEightChars() {
        AuthDtos.ForcePasswordChangeRequest r = new AuthDtos.ForcePasswordChangeRequest();
        r.setNewPassword("Abc1234");
        r.setConfirmPassword("Abc1234");
        assertThat(validator.validate(r)).isNotEmpty();
    }
}
