package com.wherelee.base.modules.identity.rbac;

import com.wherelee.base.modules.platform.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private RbacRepository rbacRepository;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void resolvesPermissionsAndCachesPerTenantAndUser() {
        when(rbacRepository.findPermissionCodesByUser(7L))
                .thenReturn(List.of("order:pay", "order:read"));
        PermissionService service = new PermissionService(rbacRepository);

        assertThat(service.has(7L, "order:pay")).isTrue();
        assertThat(service.has(7L, "admin:user:manage")).isFalse();
        assertThat(service.permissionsOf(7L)).containsExactlyInAnyOrder("order:pay", "order:read");
        verify(rbacRepository, times(1)).findPermissionCodesByUser(7L);

        TenantContext.set(1L);
        when(rbacRepository.findPermissionCodesByUser(7L)).thenReturn(List.of());
        assertThat(service.has(7L, "order:pay")).isFalse();
        verify(rbacRepository, times(2)).findPermissionCodesByUser(7L);
    }

    @Test
    void invalidateForcesReload() {
        when(rbacRepository.findPermissionCodesByUser(7L)).thenReturn(List.of("order:read"));
        PermissionService service = new PermissionService(rbacRepository);
        assertThat(service.has(7L, "order:pay")).isFalse();

        when(rbacRepository.findPermissionCodesByUser(7L)).thenReturn(List.of("order:pay"));
        service.invalidate(7L);

        assertThat(service.has(7L, "order:pay")).isTrue();
        verify(rbacRepository, times(2)).findPermissionCodesByUser(7L);
    }
}
