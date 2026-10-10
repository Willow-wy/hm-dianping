package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2. 如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        String code = RandomUtil.randomNumbers(6);

        // 3. 保存验证码到 session
        // session.setAttribute("code", code);

        // 保存验证码到 redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        log.debug("发送短信验证码成功，验证码：{}", code);



        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();

        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2. 如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }

        // 3. 从 session 中获取保存的验证码并进行比对
//        Object cacheCode = session.getAttribute("code");

        // 3. 从 redis 中获取保存的验证码并进行比对
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();

        if (cacheCode == null || !cacheCode.equals(code)) {
            // 3. 不一致，报错
            return Result.fail("验证码错误");
        }

        User user = query().eq("phone", phone).one();

        if (user == null) {
            user = createUserWithPhone(phone);
        }

        // 4. 保存用户信息到 session 中

//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        // 保存用户信息到 redis 中
        // 4.1 生成 token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        // 4.2 将 User 对象转为 Hash 存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 4.3 存储
        String tokenkey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenkey, userMap);
        // 4.4 设置 token 有效期
        stringRedisTemplate.expire(tokenkey, LOGIN_USER_TTL, TimeUnit.MINUTES);


        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;


    }
}
