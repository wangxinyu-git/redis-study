package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherOrderService_1_秒杀优化前;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
//@Service
public class VoucherOrderServiceImpl_3_一人一单_有超卖 extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService_1_秒杀优化前 {
  @Autowired
  private ISeckillVoucherService seckillVoucherService;
  @Autowired
  private RedisIdWorker redisIdWorker;
  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @Override
  @Transactional
  public Result seckillVoucher(Long voucherId) {
    //1.查询优惠券
    final SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
    //2. 判断秒杀是否开始
    if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
      //尚未开始
      return Result.fail("秒杀尚未开始！");
    }
    //3.判断秒杀是否结束
    if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
      return Result.fail("秒杀已经结束！");
    }
    //4.判断库存是否充足
    if (voucher.getStock() < 1) {
      //库存不足
      return Result.fail("库存不足!");
    }
    //5.一人一单
    Long userId = UserHolder.getUser().getId();
    Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
    if (count > 0) {
      return Result.fail("用户已购买过一次!");
    }
    //6.扣减库存
    boolean success = seckillVoucherService.update().setSql("stock=stock-1")
        .eq("voucher_id", voucherId)
        .gt("stock", 0)
        .update();
    if (!success) {
      return Result.fail("库存不足!");
    }
    //7.创建订单
    VoucherOrder voucherOrder = new VoucherOrder();
    //7.1 订单id
    Long orderId = redisIdWorker.nextId("order");
    voucherOrder.setId(orderId);
    //7.2 用户id
    voucherOrder.setUserId(userId);
    //7.3 代金券id
    voucherOrder.setVoucherId(voucherId);
    //7.4 保存数据库
    save(voucherOrder);
    //8.返回
    return Result.ok(orderId);
  }

  @Override
  public Result createVoucherOrder(Long voucherId) {
    return null;
  }
}
