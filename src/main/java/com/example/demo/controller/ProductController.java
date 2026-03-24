package com.example.demo.controller;

import com.example.demo.dto.Result;
import com.example.demo.entity.Product;
import com.example.demo.service.IProductService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/product")
public class ProductController {
    @Resource
    private IProductService productService;

    @GetMapping("/{id}")
    public Result queryById(@PathVariable("id") Long id) {
        return productService.queryById(id);
    }

    @GetMapping("/search")
    public Result search(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "pageSize", defaultValue = "5") Integer pageSize) {
        return productService.search(keyword, categoryId, current, pageSize);
    }

    @PostMapping
    public Result create(@RequestBody Product product) {
        return productService.create(product);
    }

    @PutMapping
    public Result update(@RequestBody Product product) {
        return productService.updateProduct(product);
    }
}
