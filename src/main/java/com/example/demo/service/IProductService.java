package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.dto.Result;
import com.example.demo.entity.Product;

public interface IProductService extends IService<Product> {
    Result queryById(Long id);

    Result search(String keyword, Long categoryId, Integer current, Integer pageSize);

    Result create(Product product);

    Result updateProduct(Product product);
}
