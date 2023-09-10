package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import lombok.val;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

@Component
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private  static  final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public void set(String key, Object obj, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(obj), time, unit);
    }

    public void setWithLogicalExpire(String key, Object obj, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        redisData.setData(obj);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <T, ID> T get(String keyPrefix, Class<T> type, ID id, Function<ID,T> db,Long time,TimeUnit unit) {
        String key = keyPrefix + id;
        String typeJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(typeJson)) {
            return JSONUtil.toBean(typeJson, type);
        }
        if (typeJson != null) {
            return null;
        }
        String lockKey =LOCK_SHOP_KEY+id;
        boolean isExist = tryLock(lockKey);
        try {
            if (!isExist) {
                Thread.sleep(50);
                return get(keyPrefix,type,id,db,time,unit);
            }
            typeJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(typeJson)) {
                return JSONUtil.toBean(typeJson, type);
            }
            if (typeJson != null) {
                return null;
            }
            T obj = db.apply(id);
            if (obj==null) {
                stringRedisTemplate.opsForValue().set(key,"", Duration.ofMinutes(CACHE_NULL_TTL));
                return  null;
            }
            set(key,obj,time,unit);

            return obj;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            delLock(lockKey);
        }
    }
    public <T, ID> T getLogicalExpire(String keyPrefix, Class<T> type, ID id, Function<ID,T> db,Long time,TimeUnit unit){
        String key = keyPrefix + id;
        String redisDataJson = stringRedisTemplate.opsForValue().get(key);
        RedisData redisData = JSONUtil.toBean(redisDataJson, RedisData.class);
       T obj= JSONUtil.toBean((JSONObject) redisData.getData(), type);
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            return obj;
        }
        String lockKey =LOCK_SHOP_KEY+id;
        boolean isExist = tryLock(lockKey);
        if (isExist) {
             redisDataJson = stringRedisTemplate.opsForValue().get(key);
             redisData = JSONUtil.toBean(redisDataJson, RedisData.class);
             obj= JSONUtil.toBean((JSONObject) redisData.getData(), type);
            if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
                return obj;
            }
           CACHE_REBUILD_EXECUTOR.submit(()->{
               try {
                   T t = db.apply(id);
                   setWithLogicalExpire(key,t,time,unit);
               } catch (Exception e) {
                   throw new RuntimeException(e);
               } finally {
                   delLock(lockKey);
               }
           });
        }

        return obj;
    }



    public boolean tryLock(String key){
        Boolean isExist = stringRedisTemplate.opsForValue().setIfAbsent(key, "1");
        return Boolean.TRUE.equals(isExist);
    }
    public void delLock(String key){
        stringRedisTemplate.delete(key);
    }


}
