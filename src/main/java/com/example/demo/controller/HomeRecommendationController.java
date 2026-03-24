package com.example.demo.controller;

import com.example.demo.dto.Result;
import com.example.demo.entity.HomeRecommendation;
import com.example.demo.service.IHomeRecommendationService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/home/recommendation")
public class HomeRecommendationController {
    @Resource
    private IHomeRecommendationService homeRecommendationService;

    @GetMapping("/list")
    public Result queryList(
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "limit", defaultValue = "10") Integer limit) {
        return homeRecommendationService.queryActiveList(bizType, limit);
    }

    @PostMapping
    public Result create(@RequestBody HomeRecommendation recommendation) {
        return homeRecommendationService.create(recommendation);
    }

    @PutMapping
    public Result update(@RequestBody HomeRecommendation recommendation) {
        return homeRecommendationService.updateRecommendation(recommendation);
    }
}
