package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 基于session的拦截器
 */
public class LoginInterceptor_session implements HandlerInterceptor {
  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    //1.获取session
    HttpSession session = request.getSession();
    //2.获取user
    UserDTO user = (UserDTO) session.getAttribute("user");
    //3.判断用户是否存在
    if (user == null) {
      //4.不存在,拦截,返回401
      response.setStatus(401);
      return false;
    }
    //5.存在,保存user到ThreadLocal
    UserHolder.saveUser(user);
    //6.放行
    return true;
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
    UserHolder.removeUser();
  }
}
