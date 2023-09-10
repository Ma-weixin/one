package com.hmdp.utils;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
@Component
public class RedisWorker {
    private static final Long BEGIN_TIMESTAMP=1672531200L;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
   public long nextId(String keyPrefix){
       LocalDateTime now = LocalDateTime.now();
       Long epochSecond = now.toEpochSecond(ZoneOffset.UTC);
       long timeStamp = epochSecond - BEGIN_TIMESTAMP;

       String formatTime = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
       long count = stringRedisTemplate.opsForValue().increment("incr:" + keyPrefix + ":" + formatTime);
       return timeStamp<<32 | count;

   }

}
