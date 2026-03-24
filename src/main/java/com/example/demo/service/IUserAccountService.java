package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.dto.Result;
import com.example.demo.entity.UserAccount;

public interface IUserAccountService extends IService<UserAccount> {
    Result queryByUserId(Long userId);

    Result adjustBalance(Long userId, Long delta);
}
