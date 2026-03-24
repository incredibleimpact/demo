package com.example.demo.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.example.demo.dto.UserDTO;
import com.example.demo.utils.constants.RedisConstants;
import com.example.demo.utils.login.UserHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token=request.getHeader("authorization");//每次请求客户端将Token放在Cookie或HTTP头中发送到服务,这里实现为后者
        if(StrUtil.isBlank(token)){
            return true; //token为空,直接放到第二层
        }
        String tokenKey= RedisConstants.LOGIN_USER_KEY+token;
        Map<Object,Object> map=stringRedisTemplate.opsForHash().entries(tokenKey);
        if(map.isEmpty()){
            return true; //tokenKey为空,直接放到第二层
        }
        //第二层会放行一些路径,而其他路径则会进行拦截验证
        //对于token和tokenKey为空的情况,会直接放到第二层,对于非放行路径会进行拦截验证
        //若token和tokenKey非空,则刷新redis中的tokenKey过期时间,并且将用户信息存放到ThreadLocal中
        //对于需要拦截验证的请求,第二层可以直接判断用户信息是否存在ThreadLocal中,而不需要重复查询Redis或其他数据源
        UserDTO userDTO=BeanUtil.fillBeanWithMap(map, new UserDTO(), false);
        UserHolder.saveUser(userDTO);
        stringRedisTemplate.expire(tokenKey,30, TimeUnit.MINUTES);
        return true;
    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}