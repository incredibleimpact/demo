package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.dto.Result;
import com.example.demo.entity.Category;

public interface ICategoryService extends IService<Category> {
    Result queryTree();

    Result create(Category category);

    Result updateCategory(Category category);
}
