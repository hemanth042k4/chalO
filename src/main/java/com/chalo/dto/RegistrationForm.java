package com.chalo.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegistrationForm {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    private String email;

    @NotNull(message = "Age is required")
    @Min(value = 1, message = "Please enter a valid age")
    @Max(value = 120, message = "Please enter a valid age")
    private Integer age;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}
