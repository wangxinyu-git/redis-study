package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock_3_lua脚本原子性 implements ILock {
  private String name;
  private StringRedisTemplate stringRedisTemplate;

  public SimpleRedisLock_3_lua脚本原子性(String name, StringRedisTemplate stringRedisTemplate) {
    this.name = name;
    this.stringRedisTemplate = stringRedisTemplate;
  }

  private static final String KEY_PREFIX = "lock:";
  private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
  private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
  static {
    UNLOCK_SCRIPT = new DefaultRedisScript();
    UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
    UNLOCK_SCRIPT.setResultType(Long.class);
  }
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
    // 获取线程标示
    String threadId = ID_PREFIX+Thread.currentThread().getId();
    //调用Lua脚本，保证原子性
    stringRedisTemplate.execute(UNLOCK_SCRIPT, List.of(KEY_PREFIX + name), threadId);
  }
}