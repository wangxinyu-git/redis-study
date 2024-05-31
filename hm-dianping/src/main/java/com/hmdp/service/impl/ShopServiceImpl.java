package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  /**
   * opsForValue
   * @param id
   * @return
   */
  /*@Override
  public Result queryById(Long id) {
    //1.从redis查询商铺缓存
    String key = CACHE_SHOP_KEY + id;
    String shopJson = stringRedisTemplate.opsForValue().get(key);
    //2.判断是否存在
    if (StrUtil.isNotBlank(shopJson)) {
      //3.存在,直接返回
      Shop shop = JSONUtil.toBean(shopJson, Shop.class, false);
      return Result.ok(shop);
    }
    //4.不存在,根据id查询数据库
    Shop shop = getById(id);
    if (shop == null) {
      //5.不存在,返回错误
      return Result.fail("店铺不存在!");
    }
    //6.存在,写入redis
    stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop));
    //7.返回
    return Result.ok(shop);
  }*/

  /**
   * opsForHash
   * @param id
   * @return
   */
  @Override
  public Result queryById(Long id) {
    //1.从redis查询商铺缓存
    String key = CACHE_SHOP_KEY + id;
    Map<Object, Object> shopMap = stringRedisTemplate.opsForHash().entries(key);
    //2.判断是否存在
    if (MapUtil.isNotEmpty(shopMap)) {
      //3.存在,直接返回
      Shop shop = BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
      return Result.ok(shop);
    }
    //4.不存在,根据id查询数据库
    Shop shop = getById(id);
    if (shop == null) {
      //5.不存在,返回错误
      return Result.fail("店铺不存在!");
    }
    //6.存在,写入redis
    Map<String, Object> m = BeanUtil.beanToMap(shop, new HashMap<>(),
        CopyOptions.create().ignoreNullValue().setFieldValueEditor((s, o) -> {
          if(s.equalsIgnoreCase("distance")){
            return "0";
          }
         return o.toString();
        }));
    stringRedisTemplate.opsForHash().putAll(key, m);
    //7.返回
    return Result.ok(shop);
  }
}
