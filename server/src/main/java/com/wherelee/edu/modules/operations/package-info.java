/**
 * 运营模块：管理员编排接口、订单运维、账号管理和告警。
 *
 * <p>依赖方向：operations -> identity、commerce、platform。这里是管理端入口，
 * 不把管理职责扩散到普通业务模块。</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Operations", allowedDependencies = {"platform", "identity", "course", "commerce", "learning"})

package com.wherelee.edu.modules.operations;