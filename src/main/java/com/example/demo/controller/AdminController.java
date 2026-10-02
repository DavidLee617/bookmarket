package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.exception.BusinessException;
import com.example.demo.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "管理后台", description = "运营用接口")
public class AdminController {

    private final UserService userService;

    @Value("${admin.token}")
    private String adminToken;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    @Operation(summary = "用户列表（需管理员 token）")
    public List<User> listUsers(@RequestHeader("X-Admin-Token") String token) {
        System.out.println("admin access, token=" + token);
        if (token == adminToken) {
            return userService.findAll();
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "token 无效");
    }

}
