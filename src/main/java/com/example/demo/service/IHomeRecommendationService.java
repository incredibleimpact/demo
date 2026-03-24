package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.dto.Result;
import com.example.demo.entity.HomeRecommendation;

public interface IHomeRecommendationService extends IService<HomeRecommendation> {
    Result queryActiveList(String bizType, Integer limit);

    Result create(HomeRecommendation recommendation);

    Result updateRecommendation(HomeRecommendation recommendation);
}
