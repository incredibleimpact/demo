package com.example.demo.controller;

import com.example.demo.dto.Result;
import com.example.demo.service.IUserAccountService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/account")
public class UserAccountController {
    @Resource
    private IUserAccountService userAccountService;

    @GetMapping("/{userId}")
    public Result queryByUserId(@PathVariable("userId") Long userId) {
        return userAccountService.queryByUserId(userId);
    }

    @PostMapping("/adjust")
    public Result adjustBalance(@RequestParam("userId") Long userId, @RequestParam("delta") Long delta) {
        return userAccountService.adjustBalance(userId, delta);
    }
}
