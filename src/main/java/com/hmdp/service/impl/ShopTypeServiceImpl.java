package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
// 0. 定义缓存Key（注意：这里直接使用常量，不要拼ID）
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY;

        // 1. 从 redis 查询商铺类型缓存
        // 因为存的是列表，所以需要获取整个 key 对应的值
        String shopTypeJson = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断是否存在
        if (StrUtil.isNotBlank(shopTypeJson)) {
            // 3. 存在，直接返回
            // 注意这里类型转换：将 JSON 数组字符串直接反序列化为 List<ShopType>
            List<ShopType> shopTypes = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(shopTypes);
        }

        // 4. 不存在，查询数据库
        // 注意：类型列表一般不需要根据某个 ID 查询，直接查全表并按 sort 排序
        List<ShopType> shopTypes = query().orderByAsc("sort").list();

        // 5. 判断数据库中是否存在
        if (shopTypes == null || shopTypes.isEmpty()) {
            // 6. 数据库中也没有，返回错误信息或空列表
            return Result.fail("商铺类型数据不存在");
        }

        // 7. 存在，写入 redis
        // 注意：将整个 List 对象序列化为 JSON 字符串数组
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shopTypes));

        // 8. 返回
        return Result.ok(shopTypes);
    }
}
