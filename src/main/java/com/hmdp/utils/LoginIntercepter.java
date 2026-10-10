package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginIntercepter implements HandlerInterceptor {

//    private StringRedisTemplate stringRedisTemplate;
//
//    public LoginIntercepter(StringRedisTemplate stringRedisTemplate) {
//        this.stringRedisTemplate = stringRedisTemplate;
//    }



    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        // 1. 获取session
////        HttpSession session = request.getSession();
//
//        // 1. 从request中获取token
//        String token = request.getHeader("authorization");
//        if (StrUtil.isBlank(token)) {
//            response.setStatus(401);
//            return false;
//        }
//
//        // 2. 从redis中获取用户
//        String key = RedisConstants.LOGIN_USER_KEY + token;
//        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
//        // 3. 判断用户是否存在
//
////        Object user = session.getAttribute("user");
//
//        if (userMap.isEmpty()) {
//            response.setStatus(401);
//            return false;
//        }
//        // 4. 将查询到的Hash数据转为UserDTO对象
//        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
//
//        // 5. 保存用户到ThreadLocal
//        UserHolder.saveUser(userDTO);
//
//        // 6. 刷新token有效期
//        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
//
//        // 7. 放行
//        return true;

        // 判断用户是否存在
        if (UserHolder.getUser() == null) {
            response.setStatus(401);
            return false;
        }

        return  true;

    }

//    @Override
//    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
//        UserHolder.removeUser();
//    }
}
