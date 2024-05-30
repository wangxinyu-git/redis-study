package com.hzit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzit.pojo.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

@SpringBootTest
public class SpringRedisTest2 {
  @Autowired
  private StringRedisTemplate stringRedisTemplate;
  @Test
  public void testString() {
    stringRedisTemplate.opsForValue().set("name", "赵柳");
    Object name = stringRedisTemplate.opsForValue().get("name");
    System.out.println("name = " + name);
  }
  @Test
  public void testObject() throws JsonProcessingException {
    User user = new User("王五", 28);
    ObjectMapper objectMapper = new ObjectMapper();
    String json = objectMapper.writeValueAsString(user);
    stringRedisTemplate.opsForValue().set("user:1",json);

    String userstr = stringRedisTemplate.opsForValue().get("user:1");
    User user1 = objectMapper.readValue(userstr, User.class);
    System.out.println("user1 = " + user1);
  }

  @Test
  public void testHash() {
    stringRedisTemplate.opsForHash().put("user:2","name","张三");
    stringRedisTemplate.opsForHash().put("user:2","age","22");

    Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries("user:2");
    System.out.println("entries = " + entries);
  }
}
