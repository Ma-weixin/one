package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class RedisLock implements ILock{
    private final String name;
    private final StringRedisTemplate stringRedisTemplate;
    private static final String ID_PREFIX ="lock:";
    private static final String VALUE_PREFIX= UUID.randomUUID().toString(true);
    @Override
    public boolean tryLock(long timeOutSec) {
        String id =VALUE_PREFIX+"-"+ Thread.currentThread().getId();
        Boolean blog=stringRedisTemplate.opsForValue().setIfAbsent(ID_PREFIX+name,id,timeOutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(blog);
    }

    public RedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void unLock() {
        String id =VALUE_PREFIX+ Thread.currentThread().getId();
        String key = stringRedisTemplate.opsForValue().get(ID_PREFIX + name);
        if (id.equals(key)){
            stringRedisTemplate.delete(key);
        }

    }
}
