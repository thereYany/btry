package com.wherelee.edu.modules.operations.admin;

import com.wherelee.edu.modules.platform.aop.PermissionCodes;
import com.wherelee.edu.modules.identity.rbac.RbacRepository;
import com.wherelee.edu.modules.identity.rbac.RoleAdminService;
import com.wherelee.edu.modules.platform.aop.RequirePermission;
import com.wherelee.edu.modules.platform.aop.AdminAudit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色与权限管理：租户可自定义角色、授权限、给用户授角色。
 *
 * <p>这是"新项目只改配置就能定义角色"的入口——新增角色不需要改代码。</p>
 */
@RestController
@RequestMapping("/api/admin")
public class RoleAdminController {

    private final RoleAdminService roleAdminService;

    public RoleAdminController(RoleAdminService roleAdminService) {
        this.roleAdminService = roleAdminService;
    }

    @GetMapping("/roles")
    @RequirePermission(PermissionCodes.ADMIN_ROLE_MANAGE)
    public List<RoleView> roles() {
        return roleAdminService.listRoles().stream()
                .map(role -> new RoleView(role.code(), role.name(), role.description(), role.builtIn(),
                        roleAdminService.permissionsOfRole(role.code())))
                .toList();
    }

    @GetMapping("/permissions")
    @RequirePermission(PermissionCodes.ADMIN_ROLE_MANAGE)
    public List<PermissionView> permissions() {
        return roleAdminService.listPermissions().stream()
                .map(permission -> new PermissionView(permission.code(), permission.name(),
                        permission.description()))
                .toList();
    }

    @PostMapping("/roles")
    @RequirePermission(PermissionCodes.ADMIN_ROLE_MANAGE)
    @AdminAudit(action = "ROLE_CREATE", targetType = "ROLE")
    public RoleView createRole(@Valid @RequestBody CreateRoleRequest request) {
        RbacRepository.RoleRow role = roleAdminService.createRole(
                request.code(), request.name(), request.description(), request.permissions());
        return new RoleView(role.code(), role.name(), role.description(), role.builtIn(),
                roleAdminService.permissionsOfRole(role.code()));
    }

    @PutMapping("/roles/{code}/permissions")
    @RequirePermission(PermissionCodes.ADMIN_ROLE_MANAGE)
    @AdminAudit(action = "ROLE_PERMISSIONS_UPDATE", targetType = "ROLE")
    public RoleView updatePermissions(@PathVariable String code,
                                      @Valid @RequestBody UpdatePermissionsRequest request) {
        roleAdminService.updateRolePermissions(code, request.permissions());
        RbacRepository.RoleRow role = roleAdminService.listRoles().stream()
                .filter(item -> item.code().equals(code)).findFirst()
                .orElseThrow(() -> com.wherelee.edu.modules.platform.common.BizException.badRequest("角色不存在"));
        return new RoleView(role.code(), role.name(), role.description(), role.builtIn(),
                roleAdminService.permissionsOfRole(role.code()));
    }

    @PutMapping("/users/{userId}/roles")
    @RequirePermission(PermissionCodes.ADMIN_ROLE_MANAGE)
    @AdminAudit(action = "USER_ROLES_UPDATE", targetType = "USER", targetIdArg = 0)
    public AssignResult assignRoles(@PathVariable Long userId,
                                    @Valid @RequestBody AssignRolesRequest request) {
        roleAdminService.assignRoles(userId, request.roles());
        return new AssignResult(userId, request.roles());
    }

    public record RoleView(String code, String name, String description, boolean builtIn,
                           List<String> permissions) {
    }

    public record PermissionView(String code, String name, String description) {
    }

    public record CreateRoleRequest(
            @NotBlank(message = "角色码不能为空")
            @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$", message = "角色码格式不正确")
            String code,
            @NotBlank(message = "角色名不能为空")
            @Size(max = 60, message = "角色名过长")
            String name,
            @Size(max = 200, message = "描述过长")
            String description,
            List<String> permissions) {
    }

    public record UpdatePermissionsRequest(List<String> permissions) {
    }

    public record AssignRolesRequest(
            @jakarta.validation.constraints.NotEmpty(message = "至少保留一个角色")
            List<String> roles) {
    }

    public record AssignResult(Long userId, List<String> roles) {
    }
}
