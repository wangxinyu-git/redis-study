package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
  @Autowired
  private ISeckillVoucherService seckillVoucherService;
  @Autowired
  private RedisIdWorker redisIdWorker;
  @Autowired
  private StringRedisTemplate stringRedisTemplate;
  @Autowired
  private RedissonClient redissonClient;

  private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

  //spring高版本存在循环依赖
  /*@Autowired
  IVoucherOrderService voucherOrderService;*/
  static {
    SECKILL_SCRIPT = new DefaultRedisScript<>();
    SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill2.lua"));
    SECKILL_SCRIPT.setResultType(Long.class);
  }

  private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

  @PostConstruct
  private void init() {
    final String queueName = "stream.orders";
    SECKILL_ORDER_EXECUTOR.submit(() -> {
      while (true) {
        try {
          // 1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders >
          List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
              Consumer.from("g1", "c1"),
              StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
              StreamOffset.create(queueName, ReadOffset.lastConsumed())
          );
          // 2.判断订单信息是否为空
          if (list == null || list.isEmpty()) {
            // 如果为null，说明没有消息，继续下一次循环
            continue;
          }
          //3.解析数据
          MapRecord<String, Object, Object> record = list.get(0);
          Map<Object, Object> value = record.getValue();
          VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), false);
          //4.创建订单
          handleVoucherOrder(voucherOrder);
          //5.确认消息 XACK stream.orders g1 id
          stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
        } catch (Exception e) {
          log.error("处理订单异常", e);
          handlePendingList();
        }
      }
    });
  }

  private void handlePendingList() {
    final String queueName = "stream.orders";
    while (true) {
      try {
        // 1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 STREAMS stream.orders 0
        List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
            Consumer.from("g1", "c1"),
            StreamReadOptions.empty().count(1),
            StreamOffset.create(queueName, ReadOffset.from("0"))
        );
        // 2.判断订单信息是否为空
        if (list == null || list.isEmpty()) {
          // 如果为null，说明没有异常消息，结束循环
          break;
        }
        //3.解析数据
        MapRecord<String, Object, Object> record = list.get(0);
        Map<Object, Object> value = record.getValue();
        VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), false);
        //4.创建订单
        handleVoucherOrder(voucherOrder);
        //5.确认消息 XACK stream.orders g1 id
        stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
      } catch (Exception e) {
        log.error("处理订单异常", e);
        try {
          TimeUnit.MILLISECONDS.sleep(20);
        } catch (InterruptedException ex) {
          ex.printStackTrace();
        }
      }
    }
  }

  private void handleVoucherOrder(VoucherOrder voucherOrder) {
    Long userId = voucherOrder.getUserId();
    //5.创建锁对象
    RLock lock = redissonClient.getLock("lock:order:" + userId);
    //6.获取锁
    boolean isLock = lock.tryLock();
    if (!isLock) {
      // 获取锁失败，直接返回失败或者重试
      log.error("不允许重复下单！");
    }
    try {
      proxy.createVoucherOrder(voucherOrder);
    } finally {
      lock.unlock();
    }
  }

  private IVoucherOrderService proxy;

  @Override
  public Result seckillVoucher(Long voucherId) {
    //1.用户id
    Long userId = UserHolder.getUser().getId();
    //2.事务代理对象
    proxy = (IVoucherOrderService) AopContext.currentProxy();
    //3.订单id
    long orderId = redisIdWorker.nextId("order");
    //4.执行lua脚本
    Long result = stringRedisTemplate.execute(
        SECKILL_SCRIPT,
        Collections.emptyList(),
        voucherId.toString(), userId.toString(), String.valueOf(orderId)
    );
    int r = result.intValue();
    //5.判断结果是否为0
    if (r != 0) {
      // 4.1.不为0 ，代表没有购买资格
      return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
    }
    //6.返回
    return Result.ok(orderId);
  }

  @Transactional
  public void createVoucherOrder(VoucherOrder voucherOrder) {
    //1.一人一单
    Long userId = voucherOrder.getUserId();
    Integer count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
    if (count > 0) {
      log.error("用户已购买过一次!");
      return;
    }
    //2.扣减库存
    boolean success = seckillVoucherService.update().setSql("stock=stock-1")
        .eq("voucher_id", voucherOrder.getVoucherId())
        .gt("stock", 0)
        .update();
    if (!success) {
      log.error("库存不足!");
      return;
    }
    //3 保存数据库
    save(voucherOrder);
  }

}
