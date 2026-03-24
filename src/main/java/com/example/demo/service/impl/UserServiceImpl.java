package com.example.demo.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.dto.LoginFormDTO;
import com.example.demo.dto.Result;
import com.example.demo.dto.UserDTO;
import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.IUserService;
import com.example.demo.utils.constants.RedisConstants;
import com.example.demo.utils.login.RegexUtils;
import com.example.demo.utils.constants.SystemConstants;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redisTemplate;

    @Override
    public Result sendCode(String phone) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式不正确");
        }
        String code = RandomUtil.randomNumbers(6);
        redisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY+phone,code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式不正确");
        }
        String redisCode=redisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY+phone);
        if (code == null || !code.equals(redisCode)) {
            return Result.fail("验证码不正确");
        }
        User user = this.getOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (Objects.isNull(user)) {
            user = createUserWithPhone(phone);
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> map = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        String token=UUID.randomUUID().toString(true);
        String tokenKey=RedisConstants.LOGIN_USER_KEY+token;
        redisTemplate.opsForHash().putAll(tokenKey,map);
        return Result.ok(token);//返回Result给前端,前端会解析出token,并且在以后发起的请求中都加上authorization:token
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX +RandomUtil.randomString(10));
        save(user);//向数据库插入数据
        return user;

    }
    /**
     @Override
     public Result sendCode(String phone, HttpSession session) {
     if(RegexUtils.isPhoneInvalid(phone)){
     return Result.fail("手机格式不正确");
     }
     String code= RandomUtil.randomNumbers(6);
     session.setAttribute(SystemConstants.VERIFY_CODE, code);
     return Result.ok();
     }

     @Override
     public Result login(LoginFormDTO loginForm, HttpSession session) {
     String phone=loginForm.getPhone();
     String code=loginForm.getCode();
     if(RegexUtils.isPhoneInvalid(phone)){
     return Result.fail("手机格式错误");
     }
     String sessionCode=(String) session.getAttribute(SystemConstants.VERIFY_CODE);
     if(code==null||!code.equals(sessionCode)){
     return Result.fail("验证码错误");
     }
     User user=this.getOne(new LambdaQueryWrapper<User>().eq(User::getPhone,phone));
     if(Objects.isNull(user)){
     user=createUserWithPhone(phone);
     }
     session.setAttribute(SystemConstants.LOGIN_USER,user);
     return Result.ok();
     }
     **/
}
