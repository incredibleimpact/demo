package com.example.demo.interceptor;

import com.example.demo.utils.login.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if(UserHolder.getUser()==null){
            response.setStatus(401);
            return false;
        }
        return true;
    }
    /**
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session=request.getSession();
        User user=(User) session.getAttribute(SystemConstants.LOGIN_USER);
        if(Objects.isNull(user)){
            response.setStatus(HttpStatus.error400().status());
            return false;
        }
        //UserHolder.saveUser(user);
        return HandlerInterceptor.super.preHandle(request,response,handler);
    }
    **/
}
