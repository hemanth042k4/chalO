package com.chalo.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Backing object for the Host Adventure form (/adventures/new).
 * Field names map onto the Adventure entity in AdventureController:
 *   location       -> locationName
 *   availableSlots -> maxParticipants
 *   tagIds         -> tags (via TagRepository.findByIdIn)
 *   photoUrls      -> photos (cascaded AdventurePhoto rows)
 */
@Data
public class AdventureForm {

    @NotBlank(message = "Title is required")
    @Size(max = 150, message = "Title must not exceed 150 characters")
    private String title;

    @NotBlank(message = "Description is required")
    private String description;

    @NotBlank(message = "Location is required")
    @Size(max = 255, message = "Location must not exceed 255 characters")
    private String location;

    @NotNull(message = "Date is required")
    @FutureOrPresent(message = "Date must be today or in the future")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate adventureDate;

    @NotNull(message = "Available slots is required")
    @Min(value = 1, message = "There must be at least 1 slot")
    @Max(value = 1000, message = "That's a lot of slots — keep it under 1000")
    private Integer availableSlots;

    // At least one interest must be selected
    @NotEmpty(message = "Select at least one interest")
    private List<Long> tagIds = new ArrayList<>();

    // Optional image URLs (MVP). Blank entries are filtered server-side.
    private List<String> photoUrls = new ArrayList<>();
}
