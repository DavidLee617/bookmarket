package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BookRequest {

    @NotBlank
    private String title;

    private String author;

    private String isbn;

    @NotNull
    @PositiveOrZero
    private BigDecimal price;

    @PositiveOrZero
    private int stock;

}
