package com.intheeast.controllers;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TestController {

    // 1. 첫 접속 시 호출하여 CSRF 쿠키를 받음
    @GetMapping("/init")
    public String init() {
        return "CSRF Cookie Initialized!";
    }

    // 2. CSRF 토큰이 헤더에 없으면 403 에러가 남
    @PostMapping("/data")
    public String receiveData(@RequestBody Map<String, Object> data) {
        return "Post Success! Received: " + data.get("name");
    }
}