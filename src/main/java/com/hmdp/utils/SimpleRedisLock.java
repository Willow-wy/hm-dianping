package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;


public class SimpleRedisLock implements ILock {
    private String name;

    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";


    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取当前线程的id

        String threadId = ID_PREFIX + Thread.currentThread().getId();

        // 尝试获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        // 获取当前线程的id
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 比较是否一致
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if (threadId.equals(id)) {
            // 删除锁
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }




    }
}
