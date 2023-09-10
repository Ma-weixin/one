package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

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
    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryForId(Long id) {
        Shop shop = cacheClient.get(CACHE_SHOP_KEY, Shop.class, 1, this::getById, 2L, TimeUnit.MINUTES);
        Shop shop1 = cacheClient.getLogicalExpire(CACHE_SHOP_KEY, Shop.class, 1, this::getById, 2L, TimeUnit.MINUTES);
        if (shop==null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);

    }


    @Override
    @Transactional
    public Result updateByShop(Shop shop) {
        Long id = shop.getId();
        if (id==null) {
            return Result.fail("输入正确的商铺序号");
        }
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY+id);
        return Result.ok();
    }

}
