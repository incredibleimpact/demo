package com.example.demo.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.dto.Result;
import com.example.demo.entity.Shop;
import com.example.demo.service.IShopService;
import com.example.demo.utils.constants.SystemConstants;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shop")
public class ShopController {
    @Resource
    public IShopService shopService;

    @GetMapping("/query/{id}")
    public Result queryShopById(@PathVariable("id") Long id) throws InterruptedException {
        return shopService.queryById(id);
    }

    @PostMapping("/save")
    public Result saveShop(@RequestBody Shop shop) {
        shopService.save(shop);
        return Result.ok(shop.getId());
    }

    @PutMapping("/update")
    public Result updateShop(@RequestBody Shop shop) {
        return shopService.update(shop);
    }


    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }
}
