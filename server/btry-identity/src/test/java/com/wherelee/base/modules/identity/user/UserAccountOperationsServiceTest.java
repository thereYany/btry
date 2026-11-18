package com.wherelee.base.modules.identity.user;

import com.wherelee.base.modules.identity.rbac.RbacRepository;
import com.wherelee.base.modules.platform.common.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountOperationsServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RbacRepository rbacRepository;

    private UserAccountOperationsService service;

    @BeforeEach
    void setUp() {
        service = new UserAccountOperationsService(userRepository, passwordEncoder, rbacRepository);
    }

    @Test
    void cannotDisableOwnAccount() {
        assertThatThrownBy(() -> service.changeStatus(1L, 1L, "DISABLED"))
                .isInstanceOf(BizException.class)
                .hasMessage("不能修改自己的账号状态");

        verifyNoInteractions(userRepository, passwordEncoder, rbacRepository);
    }

    @Test
    void cannotResetOwnPassword() {
        assertThatThrownBy(() -> service.resetPassword(1L, 1L, "NewPass123"))
                .isInstanceOf(BizException.class)
                .hasMessage("请通过修改密码修改自己的密码");

        verifyNoInteractions(userRepository, passwordEncoder, rbacRepository);
    }

    @Test
    void createRejectsDuplicateUsername() {
        when(userRepository.findByUsername("member1"))
                .thenReturn(Optional.of(user(9L, "MEMBER", "ACTIVE")));

        assertThatThrownBy(() -> service.createUser("member1", "Pass1234", "成员一", "MEMBER"))
                .isInstanceOf(BizException.class)
                .hasMessage("用户名已存在");

        verify(userRepository, never()).create(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void createsUserWithForcedPasswordChange() {
        when(userRepository.findByUsername("member1")).thenReturn(Optional.empty());
        when(rbacRepository.findRoleId("MEMBER")).thenReturn(Optional.of(1L));
        when(passwordEncoder.encode("Pass1234")).thenReturn("encoded");
        when(userRepository.create("member1", "encoded", "成员一", "MEMBER", true)).thenReturn(10L);

        assertThat(service.createUser("member1", "Pass1234", "成员一", "MEMBER")).isEqualTo(10L);
        verify(userRepository).create("member1", "encoded", "成员一", "MEMBER", true);
    }

    @Test
    void createRejectsUnknownRole() {
        when(userRepository.findByUsername("member1")).thenReturn(Optional.empty());
        when(rbacRepository.findRoleId("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createUser("member1", "Pass1234", "成员一", "NOPE"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("角色不存在");
    }

    @Test
    void disablingAccountBumpsTokenVersionThroughRepository() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "USER", "ACTIVE")));

        service.changeStatus(1L, 2L, "DISABLED");

        verify(userRepository).disable(2L);
    }

    @Test
    void passwordResetRequiresAnotherChangeAndBumpsTokenVersion() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "USER", "ACTIVE")));
        when(passwordEncoder.encode("NewPass123")).thenReturn("encoded");

        service.resetPassword(1L, 2L, "NewPass123");

        verify(userRepository).resetPassword(2L, "encoded");
    }

    private User user(Long id, String role, String status) {
        return new User(id, 0L, "user" + id, "hash", "用户" + id, role, status, false, 0);
    }
}