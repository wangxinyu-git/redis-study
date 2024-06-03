package com.hmdp;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@Slf4j
public class RedissonTest {
  @Resource
  private RedissonClient redissonClient;
  private RLock lock;

  @BeforeEach
  public void setUp() {
    lock = redissonClient.getLock("order");
  }

  @Test
  public void method1() throws InterruptedException {
    boolean isLock = lock.tryLock(1, TimeUnit.SECONDS);
    if (!isLock) {
      log.error("获取锁失败....1");
      return;
    }
    try {
      log.info("获取锁成功....1");
      method2();
      log.info("开始业务....1");
    } finally {
      log.warn("准备释放锁....1");
      lock.unlock();
    }
  }

  private void method2() {
    boolean isLock = lock.tryLock();
    if (!isLock) {
      log.error("获取锁失败...2");
      return;
    }
    try {
      log.info("获取锁成功....2");
      log.info("开始业务....2");
    } finally {
      log.warn("准备释放锁....2");
      lock.unlock();
    }
  }
}