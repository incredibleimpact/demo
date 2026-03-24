package com.example.demo.utils.limiter;

import com.example.demo.utils.login.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 限流维度上下文：从请求中提取 IP、用户ID
 */
@Component
public class RateLimitContextHolder {

    /**
     * 获取真实客户端 IP（兼容 Nginx 代理）
     */
    public String getClientIp() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attrs==null?null:attrs.getRequest();
        if(request==null) return "unknown";

        String[] headerNames = {
                "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP",
                "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"
        };

        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For 可能包含多个IP，取第一个
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    public String getCurrentUserId() {
        Long userId = UserHolder.getUser().getId();
        return userId != null ? userId.toString() : null;
    }
}