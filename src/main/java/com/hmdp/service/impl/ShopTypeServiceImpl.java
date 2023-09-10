package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Result queryTypeList() {
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());


        List<Object> shopTypeList = new ArrayList<>();


        Set<Object> shopTypeSet = redisTemplate.opsForZSet().range("shop:types:key",0,-1);
        if (shopTypeSet != null && !shopTypeSet.isEmpty()) {
            for (Object o : shopTypeSet) {
                ShopType type = JSONUtil.toBean((String) o, ShopType.class);
                shopTypeList.add(type);
            }
            return Result.ok(shopTypeList);

        }


        List<ShopType> typeList = query().orderByAsc("sort").list();
        if (typeList == null || typeList.isEmpty()) {
            return Result.fail("没有商店类型");
        }
        for (ShopType type : typeList) {
            String jsonStr = JSONUtil.toJsonStr(type);

            redisTemplate.opsForZSet().add("shop:types:key", jsonStr, type.getSort());
        }

        return Result.ok(typeList);
    }
}
