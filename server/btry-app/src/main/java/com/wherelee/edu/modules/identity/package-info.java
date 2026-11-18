/**
 * 账号与访问身份模块：登录、用户、JWT、角色校验依赖和教师邀请。
 *
 * <p>依赖方向：identity -> platform。</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Identity", allowedDependencies = {"platform", "contracts"})

package com.wherelee.edu.modules.identity;