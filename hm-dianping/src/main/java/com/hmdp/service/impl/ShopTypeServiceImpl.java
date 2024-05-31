package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.map.MapUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
  @Autowired
  StringRedisTemplate stringRedisTemplate;
  @Override
  public Result queryTypeList() {
    String key = CACHE_SHOPTYPE_KEY;
    //先查缓存
    final String value = stringRedisTemplate.opsForValue().get(key);
    //如果缓存不为空则直接返回
    if(value!=null){
      return Result.ok(JSONUtil.toList(value,ShopType.class));
    }
    //缓存为空则查询数据库
    final List<ShopType> shopTypeList = query().orderByAsc("sort").list();
    //将查询到的信息保存到缓存
    stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shopTypeList),CACHE_SHOPTYPE_TTL, TimeUnit.MINUTES);

    //响应数据
    return Result.ok(shopTypeList);
  }
}
