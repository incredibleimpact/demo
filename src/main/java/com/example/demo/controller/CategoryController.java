package com.example.demo.controller;

import com.example.demo.dto.Result;
import com.example.demo.entity.Category;
import com.example.demo.service.ICategoryService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/category")
public class CategoryController {
    @Resource
    private ICategoryService categoryService;

    @GetMapping("/tree")
    public Result queryTree() {
        return categoryService.queryTree();
    }

    @PostMapping
    public Result create(@RequestBody Category category) {
        return categoryService.create(category);
    }

    @PutMapping
    public Result update(@RequestBody Category category) {
        return categoryService.updateCategory(category);
    }
}
