package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.dto.Result;
import com.example.demo.entity.Shop;

public interface IShopService extends IService<Shop> {
    Result queryById(Long id) throws InterruptedException;
    Result update(Shop shop);
}
