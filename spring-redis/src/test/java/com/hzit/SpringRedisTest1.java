package com.hzit;

import com.hzit.pojo.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

@SpringBootTest
public class SpringRedisTest1 {
  @Autowired
  private RedisTemplate<String,Object> redisTemplate;
  @Test
  public void testString() {
    redisTemplate.opsForValue().set("name", "张三");
    Object name = redisTemplate.opsForValue().get("name");
    System.out.println("name = " + name);
  }
  @Test
  public void testObject() {
    redisTemplate.opsForValue().set("user:1",new User("李四",18));
    User name = (User) redisTemplate.opsForValue().get("user:1");
    System.out.println("name = " + name);
  }
}
