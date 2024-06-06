package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisConnectionUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

@SpringBootTest
public class JmeterTokensTest {
  @Autowired
  private IUserService userService;
  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @Test
  public void bulkLogin() throws Exception {
    List<User> users = userService.list().subList(0, 1000);
    //System.out.println(list);
    // 1. 创建文件
    File file = new File(System.getProperty("user.dir") + "\\src\\main\\resources\\tokens.txt");
    if (file.exists()) {
      file.delete();
    }
    file.createNewFile();
    // 2. 输出
    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
    users.forEach(user -> {
      // 7.1.随机生成token，作为登录令牌
      String token = UUID.randomUUID().toString(true);
      // 7.2.将User对象转为HashMap存储
      UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
      // 7.3.存储
      Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
          CopyOptions.create().ignoreNullValue().setFieldValueEditor((s, o) -> o.toString()));
      String tokenKey = LOGIN_USER_KEY + token;
      stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
      // 7.4.设置token有效期
      stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
//      RedisConnectionUtils.unbindConnection(stringRedisTemplate.getConnectionFactory());
      try {
        //开始写入
        bw.write(token + "\n");
        //强制刷新
        bw.flush();
      } catch (IOException e) {
        e.printStackTrace();
      }
    });
    bw.close();
    System.out.println("运行成功！");
  }
}
