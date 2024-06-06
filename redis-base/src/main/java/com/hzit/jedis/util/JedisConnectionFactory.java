package com.hzit.jedis.util;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;

public class JedisConnectionFactory {
  private static JedisPool jedisPool;

  static {
    // 配置连接池
    JedisPoolConfig poolConfig = new JedisPoolConfig();
    poolConfig.setMaxTotal(8);
    poolConfig.setMaxIdle(8);
    poolConfig.setMinIdle(0);
    poolConfig.setMaxWait(Duration.ofMillis(1000));
    // 创建连接池对象，参数：连接池配置、服务端ip、服务端端口、超时时间、密码
    jedisPool = new JedisPool(poolConfig, "192.168.25.132", 6379, 1000);
  }

  public static Jedis getJedis() {
    return jedisPool.getResource();
  }
}
