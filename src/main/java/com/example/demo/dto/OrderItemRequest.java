package com.example.demo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderItemRequest {

    @NotNull
    private Long bookId;

    /** 改为包装类型，便于前端不传时区分"未填写"与 0 */
    @Min(1)
    private Integer quantity;

}
