package com.example.demo.controller;

import com.example.demo.utils.limiter.RateLimit;
import com.example.demo.dto.LoginFormDTO;
import com.example.demo.dto.Result;
import com.example.demo.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    IUserService userService;
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone){
        return userService.sendCode(phone);
    }
    @PostMapping("/login")
    @RateLimit(
            ipEnable = true, ipCount = 5, ipWindow = 60,
            message = "登录尝试过于频繁，请60秒后重试"
    )
    public Result login(@RequestBody LoginFormDTO loginFormDTO){
        return userService.login(loginFormDTO);
    }
}
