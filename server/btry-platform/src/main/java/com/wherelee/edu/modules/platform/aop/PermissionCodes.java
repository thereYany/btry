package com.wherelee.edu.modules.platform.aop;

/**
 * 权限码目录（接口鉴权的稳定契约）。
 *
 * <p>角色是租户可配置的权限集合，权限码才是代码里 @RequirePermission 的引用对象。
 * 新增业务能力时在这里加常量，再给角色授权即可，不需要改鉴权逻辑。</p>
 */
public final class PermissionCodes {

    public static final String COURSE_CATALOG_READ = "course:catalog:read";
    public static final String COURSE_SERIES_READ = "course:series:read";
    public static final String ORDER_CREATE = "order:create";
    public static final String ORDER_READ = "order:read";
    public static final String ORDER_PAY = "order:pay";
    public static final String ORDER_CANCEL = "order:cancel";
    public static final String PAYMENT_MOCK = "payment:mock";
    public static final String LEARNING_PROGRESS_WRITE = "learning:progress:write";
    public static final String TEACHER_SERIES_MANAGE = "teacher:series:manage";
    public static final String TEACHER_CONTENT_MANAGE = "teacher:content:manage";
    public static final String TEACHER_VIDEO_UPLOAD = "teacher:video:upload";
    public static final String ADMIN_USER_MANAGE = "admin:user:manage";
    public static final String ADMIN_ORDER_MANAGE = "admin:order:manage";
    public static final String ADMIN_SERIES_MANAGE = "admin:series:manage";
    public static final String ADMIN_INVITATION_MANAGE = "admin:invitation:manage";
    public static final String ADMIN_RECON_MANAGE = "admin:recon:manage";
    public static final String ADMIN_ROLE_MANAGE = "admin:role:manage";

    private PermissionCodes() {
    }
}
