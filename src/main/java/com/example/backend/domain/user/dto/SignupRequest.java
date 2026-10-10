
package com.example.backend.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(

        @NotBlank
        @Size(max = 255)
        String loginId,

        @NotBlank
        @Size(min = 8, max = 72)
        String password,

        @NotBlank
        @Size(max = 255)
        String name,

        Long hp,

        @Email
        @Size(max = 255)
        String email

) {
}
