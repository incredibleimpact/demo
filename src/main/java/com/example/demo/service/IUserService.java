package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.dto.LoginFormDTO;
import com.example.demo.dto.Result;
import com.example.demo.entity.User;
import jakarta.servlet.http.HttpSession;

public interface IUserService extends IService<User> {
    Result sendCode(String phone);

    Result login(LoginFormDTO loginForm);

}
