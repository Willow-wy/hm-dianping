package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        // 1. 缓存穿透
//        Shop shop = queryWithPassThrough(id);

        // 2. 缓存击穿

//        Shop shop = queryWithMutex(id);

        // 3. 缓存逻辑过期
        Shop shop = queryWithLogicalExpire(id);
        if(shop == null){
            return Result.fail("商铺不存在");
        }
        // 8. 返回
        return Result.ok(shop);
    }

    // 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public Shop queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;

        // 1. 从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            // 3. 不存在，直接返回
            return null;
        }

        // 4. 命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONUtil.parseObj(redisData.getData())), Shop.class);
        //(JSONUtil.parseObj(redisData.getData()))
        // 上面这部分应该为 JSONObject jsonObject = JSONUtil.parseObj(redisData.getData());
        LocalDateTime expireTime = redisData.getExpireTime();

        // 5. 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 6. 未过期，直接返回店铺信息
            return shop;
        }

        // 7. 已过期，需要缓存重建
        // 7.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = trylock(lockKey);
        // 7.2 判断是否获取锁成功
        if (isLock) {
            // 7.3 获取到锁，开启独立线程之前，做 DoubleCheck
            String redisShopJson = stringRedisTemplate.opsForValue().get(key);
            RedisData doubleCheckData = JSONUtil.toBean(redisShopJson, RedisData.class);
            LocalDateTime expireTime_Check = doubleCheckData.getExpireTime();

            // 如果发现缓存已经被其他线程重建且没有过期，直接释放锁，无需重复开启线程
            // 这里根据你 saveShop2Redis 里设置的过期时间来判断是否依然有效
            // (注意：这里只是一个防重复提交的双检，如果已经过期，就继续往下执行异步线程)
            if (expireTime_Check.isAfter(LocalDateTime.now())) {
                // 缓存还有效，直接解锁走人
                unlock(lockKey);
            }
            else {
                // 7.3 成功，开启独立线程，实现缓存重建
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        // 重建缓存
                        this.saveShop2Redis(id, 20L);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        // 释放锁
                        unlock(lockKey);
                    }
                });
            }
        }


        // 8. 返回
        return shop;
    }

    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;

        // 1. 从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3. 存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 判断命中的是否是空值
        if (shopJson != null) {
            // 返回错误信息
            return null;
        }

        // 尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            Boolean isLock = trylock(lockKey);
            // 判断是否获取锁成功
            if (!BooleanUtil.isTrue(isLock)) {
                // 获取锁失败，休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            String shopJson_double = stringRedisTemplate.opsForValue().get(key);
// 如果缓存中已经有了（比如被前面进来的线程重建了），直接返回，不用查数据库
            if (StrUtil.isNotBlank(shopJson_double)) {
                // 注意：这里要把json转为Shop对象返回，或者直接返回json（根据你方法的返回值而定）
                return JSONUtil.toBean(shopJson_double, Shop.class);
            }
            // 4.5 DoubleCheck命中空值
            if (shopJson_double != null) {
                return null;
            }


            // 4. 不存在，根据id查询数据库
            shop = getById(id);
            // 5. 判断是否存在
            if (shop == null) {
                // 6. 不存在，返回错误信息
                // 返回空值到redis，防止缓存穿透
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 7. 存在，写入redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            unlock(lockKey);
        }
        // 8. 返回
        return shop;
    }



    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;

        // 1. 从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3. 存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 判断命中的是否是空值
        if (shopJson != null) {
            // 返回错误信息
            return null;
        }

        // 4. 不存在，根据id查询数据库
        Shop shop = getById(id);
        // 5. 判断是否存在
        if (shop == null) {
            // 6. 不存在，返回错误信息
            // 返回空值到redis，防止缓存穿透
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 7. 存在，写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 8. 返回
        return shop;
    }

    public Boolean trylock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    public void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        // 1. 查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200); // 模拟重建延时
        // 2. 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3. 写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }


    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("商铺id不能为空");
        }

        // 1. 更新数据库
        updateById(shop);
        // 2. 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);



        return Result.ok();
    }
}
