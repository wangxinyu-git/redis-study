package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

@Component
@Slf4j
public class CacheClient {
  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  //通用方法，为String类型的key设置过期时间
  public void set(String key, Object value, Long time, TimeUnit unit) {
    stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
  }

  //设置逻辑过期时间
  public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
    //设置逻辑过期时间
    final RedisData redisData = new RedisData();
    redisData.setData(value);
    redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
    //写入Redis
    stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
  }

  /**
   * 解决缓存穿透
   *
   * @param keyPrefix  前缀
   * @param id         参数id
   * @param type       返回类型class
   * @param dbFallback 查询数据库方法
   * @param time       过期时间
   * @param unit       过期时间单位
   * @param <R>        返回类型
   * @param <ID>       参数类型
   * @return
   */
  public <R, ID> R queryWithPassThrough(
      String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback,
      Long time, TimeUnit unit) {
    //1.从redis查询商铺缓存
    String key = keyPrefix + id;
    String json = stringRedisTemplate.opsForValue().get(key);
    //2.判断是否存在
    if (StrUtil.isNotBlank(json)) {
      //3.存在,直接返回
      return JSONUtil.toBean(json, type, false);
    }
    //判断命中是否是空("")值
    if ("".equals(json)) {
      //返回一个错误信息
      return null;
    }
    //4.不存在,根据id查询数据库
    R r = dbFallback.apply(id);
    if (r == null) {
      //5.不存在,返回错误
      //将空值写入redis
      stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
      return null;
    }
    //6.存在,写入redis
    this.set(key, r, time, unit);
    //7.返回
    return r;
  }

  private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

  /**
   * 逻辑过期解决缓存击穿
   *
   * @param keyPrefix  前缀
   * @param id         参数id
   * @param type       返回类型class
   * @param dbFallback 查询数据库方法
   * @param time       过期时间
   * @param unit       过期时间单位
   * @param <R>        返回类型
   * @param <ID>       参数类型
   * @return
   */
  public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback,
                                          Long time, TimeUnit unit) {
    //1.从redis查询商铺缓存
    String key = keyPrefix + id;
    String json = stringRedisTemplate.opsForValue().get(key);
    //2.判断是否存在
    if (StrUtil.isBlank(json)) {
      //3.不存在,直接返回
      return null;
    }
    //4.命中，先把json反序列化为对象
    RedisData redisData = JSONUtil.toBean(json, RedisData.class);
    JSONObject data = (JSONObject) redisData.getData();
    R r = JSONUtil.toBean(data, type);
    LocalDateTime expireTime = redisData.getExpireTime();
    //5.判断是否过期
    if (expireTime.isAfter(LocalDateTime.now())) {
      //5.1 未过期，直接返回店铺信息
      return r;
    }
    //6 已过期，需要缓存重建
    //6.1 获取互斥锁
    String lockKey = LOCK_SHOP_KEY + id;
    final boolean isLock = tryLock(lockKey);
    //6.2 判断是否获取锁成功
    if (isLock) {
      //6.3 成功，开启独立线程，实现缓存重建
      CACHE_REBUILD_EXECUTOR.submit(() -> {
        try {
          //查询数据库
          R r1 = dbFallback.apply(id);
          //模拟重建缓存超时
          Thread.sleep(200);
          //写入redis
          this.setWithLogicalExpire(key, r1, time, unit);
        } catch (Exception e) {
          throw new RuntimeException(e);
        } finally {
          unlock(lockKey);
        }
      });
    }
    //7.返回过期的信息(不管锁成功或者失败都要返回)
    return r;
  }
  /**
   * 获取锁
   *
   * @param key
   * @return
   */
  private boolean tryLock(String key) {
    final Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
    return BooleanUtil.isTrue(flag);
  }

  /**
   * 释放锁
   *
   * @param key
   */
  private void unlock(String key) {
    stringRedisTemplate.delete(key);
  }
}