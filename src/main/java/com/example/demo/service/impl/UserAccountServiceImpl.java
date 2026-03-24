package com.example.demo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.dto.Result;
import com.example.demo.entity.UserAccount;
import com.example.demo.mapper.UserAccountMapper;
import com.example.demo.service.IUserAccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountServiceImpl extends ServiceImpl<UserAccountMapper, UserAccount> implements IUserAccountService {
    @Override
    public Result queryByUserId(Long userId) {
        UserAccount account = getById(userId);
        if (account == null) {
            return Result.fail("account not found");
        }
        return Result.ok(account);
    }

    @Override
    @Transactional
    public Result adjustBalance(Long userId, Long delta) {
        UserAccount account = getById(userId);
        if (account == null) {
            return Result.fail("account not found");
        }
        long newBalance = account.getBalance() + delta;
        if (newBalance < 0) {
            return Result.fail("insufficient balance");
        }
        account.setBalance(newBalance);
        updateById(account);
        return Result.ok(account);
    }
}
