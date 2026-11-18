package com.wherelee.edu.modules.platform.aop;

/**
 * 通用权限码目录（接口鉴权的稳定契约）。
 *
 * <p>只放与业务无关的内核能力权限码；垂直业务（课程、订单、学习等）的权限码
 * 由各自垂直应用维护，不要加回这里——内核不认识任何垂直业务语义。</p>
 *
 * <p>角色是租户可配置的权限集合，权限码才是代码里 {@code @RequirePermission}
 * 的引用对象。新增内核能力时在这里加常量，再给角色授权即可。</p>
 */
public final class PermissionCodes {

    public static final String ADMIN_USER_MANAGE = "admin:user:manage";
    public static final String ADMIN_ROLE_MANAGE = "admin:role:manage";
    public static final String ADMIN_INVITATION_MANAGE = "admin:invitation:manage";
    public static final String ADMIN_RECON_MANAGE = "admin:recon:manage";
    public static final String PAYMENT_MOCK = "payment:mock";

    private PermissionCodes() {
    }
}
