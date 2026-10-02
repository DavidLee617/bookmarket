package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PayOrderRequest {

    @NotNull
    private Long userId;

}
