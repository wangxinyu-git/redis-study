package com.hzit;

import com.hzit.jedis.util.JedisConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.util.Map;

public class JeditTest2 {
  private Jedis jedis;

  @BeforeEach
  public void setUp() {
    //1. 建立连接
    jedis = JedisConnectionFactory.getJedis();
    //2. 设置密码
//    jedis.auth("root");
    //3. 选择库
    jedis.select(0);
  }
  @AfterEach
  public void tearDown() {
    if (null != jedis) {
      jedis.close();
    }
  }
  @Test
  public void testString() {
    String result = jedis.set("name", "wangwu");
    System.out.println("result = " + result);
    String name = jedis.get("name");
    System.out.println("name = " + name);
  }
  @Test
  void testHash(){
    jedis.hset("reggie:user:1","name","Jack");
    jedis.hset("reggie:user:2","name","Rose");
    jedis.hset("reggie:user:1","age","21");
    jedis.hset("reggie:user:2","age","18");
    Map<String, String> map = jedis.hgetAll("reggie:user:1");
    System.out.println(map);
  }
}