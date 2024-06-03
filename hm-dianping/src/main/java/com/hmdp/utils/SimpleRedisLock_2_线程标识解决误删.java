package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock_2_线程标识解决误删 implements ILock {
  private String name;
  private StringRedisTemplate stringRedisTemplate;

  public SimpleRedisLock_2_线程标识解决误删(String name, StringRedisTemplate stringRedisTemplate) {
    this.name = name;
    this.stringRedisTemplate = stringRedisTemplate;
  }

  private static final String KEY_PREFIX = "lock:";
  private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
  @Override
  public boolean tryLock(long timeoutSec) {
    // 获取线程标示
    String threadId = ID_PREFIX+Thread.currentThread().getId();
    // 获取锁
    Boolean success = stringRedisTemplate.opsForValue()
        .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
    return Boolean.TRUE.equals(success);
  }

  @Override
  public void unlock() {
    //获取线程标识
    String threadId = ID_PREFIX + Thread.currentThread().getId();
    //获取锁中的标识
    String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
    //判断标识是否一致
    if (threadId.equals(id)) {
      //释放锁
      stringRedisTemplate.delete(KEY_PREFIX + name);
    }
  }
}