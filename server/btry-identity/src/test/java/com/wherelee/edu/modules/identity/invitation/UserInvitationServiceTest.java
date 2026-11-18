package com.wherelee.edu.modules.identity.invitation;

import com.wherelee.edu.modules.platform.common.BizException;
import com.wherelee.edu.modules.identity.invitation.dto.UserInvitationDtos;
import com.wherelee.edu.modules.identity.rbac.RbacRepository;
import com.wherelee.edu.modules.identity.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserInvitationServiceTest {

    @Mock
    private UserInvitationMapper mapper;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private InvitationTokenService tokenService;
    @Mock
    private RbacRepository rbacRepository;

    private UserInvitationService service;

    @BeforeEach
    void setUp() {
        InvitationProperties properties = new InvitationProperties(
                "https://learn.example.com/", 72, 20, 10);
        service = new UserInvitationService(
                mapper, userRepository, passwordEncoder, tokenService, rbacRepository, properties);
    }

    @Test
    void createStoresOnlyTokenHashAndReturnsOneTimeLink() {
        when(userRepository.findByUsername("physics_member")).thenReturn(Optional.empty());
        when(rbacRepository.findRoleId("MEMBER")).thenReturn(Optional.of(1L));
        when(mapper.findPendingByUsername(eq("physics_member"), any(), eq(0L))).thenReturn(null);
        when(tokenService.generate()).thenReturn("plain-token");
        when(tokenService.hash("plain-token")).thenReturn("a".repeat(64));
        doAnswer(invocation -> {
            UserInvitation invitation = invocation.getArgument(0);
            invitation.setId(11L);
            return 1;
        }).when(mapper).insert(any(UserInvitation.class));

        UserInvitationDtos.CreatedResponse response = service.create(
                new UserInvitationDtos.CreateRequest("physics_member", "物理作者", "MEMBER"), 1L);

        ArgumentCaptor<UserInvitation> captor = ArgumentCaptor.forClass(UserInvitation.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isEqualTo("a".repeat(64));
        assertThat(captor.getValue().getTokenHash()).doesNotContain("plain-token");
        assertThat(response.inviteUrl()).isEqualTo("https://learn.example.com/invite/plain-token");
    }

    @Test
    void rejectsDuplicatePendingInvitation() {
        when(userRepository.findByUsername("physics_member")).thenReturn(Optional.empty());
        when(rbacRepository.findRoleId("MEMBER")).thenReturn(Optional.of(1L));
        when(mapper.findPendingByUsername(eq("physics_member"), any(), eq(0L)))
                .thenReturn(pendingInvitation(LocalDateTime.now().plusHours(1)));

        assertThatThrownBy(() -> service.create(
                new UserInvitationDtos.CreateRequest("physics_member", "物理作者", "MEMBER"), 1L))
                .isInstanceOf(BizException.class)
                .hasMessage("该用户名已有未过期的用户邀请");
    }

    @Test
    void acceptCreatesUserWithSelfChosenPassword() {
        UserInvitation invitation = pendingInvitation(LocalDateTime.now().plusHours(1));
        when(tokenService.hash("plain-token")).thenReturn("hash");
        when(mapper.findByTokenHashForUpdate("hash", 0L)).thenReturn(invitation);
        when(userRepository.findByUsername("physics_member")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Password1234")).thenReturn("encoded");
        when(userRepository.create("physics_member", "encoded", "物理作者", "MEMBER", false))
                .thenReturn(9L);
        when(mapper.markUsed(eq(11L), eq(9L), any(), eq(0L))).thenReturn(1);

        Long userId = service.accept(new UserInvitationDtos.AcceptRequest(
                "plain-token", "Password1234", "Password1234"));

        assertThat(userId).isEqualTo(9L);
        verify(userRepository).create("physics_member", "encoded", "物理作者", "MEMBER", false);
        verify(mapper).markUsed(eq(11L), eq(9L), any(), eq(0L));
    }

    @Test
    void acceptRejectsExpiredInvitation() {
        when(tokenService.hash("plain-token")).thenReturn("hash");
        when(mapper.findByTokenHashForUpdate("hash", 0L))
                .thenReturn(pendingInvitation(LocalDateTime.now().minusSeconds(1)));

        assertThatThrownBy(() -> service.accept(new UserInvitationDtos.AcceptRequest(
                "plain-token", "Password1234", "Password1234")))
                .isInstanceOf(BizException.class)
                .hasMessage("邀请无效或已过期");

        verify(userRepository, never()).create(any(), any(), any(), any(), eq(false));
    }

    @Test
    void acceptRejectsUsedInvitation() {
        UserInvitation invitation = pendingInvitation(LocalDateTime.now().plusHours(1));
        invitation.setStatus("USED");
        when(tokenService.hash("plain-token")).thenReturn("hash");
        when(mapper.findByTokenHashForUpdate("hash", 0L)).thenReturn(invitation);

        assertThatThrownBy(() -> service.accept(new UserInvitationDtos.AcceptRequest(
                "plain-token", "Password1234", "Password1234")))
                .isInstanceOf(BizException.class)
                .hasMessage("邀请无效或已过期");
    }

    @Test
    void acceptRejectsPasswordMismatch() {
        assertThatThrownBy(() -> service.accept(new UserInvitationDtos.AcceptRequest(
                "plain-token", "Password1234", "Password5678")))
                .isInstanceOf(BizException.class)
                .hasMessage("两次输入的密码不一致");
    }

    @Test
    void revokeRejectsUsedInvitation() {
        UserInvitation invitation = pendingInvitation(LocalDateTime.now().plusHours(1));
        invitation.setStatus("USED");
        when(mapper.findById(11L, 0L)).thenReturn(invitation);

        assertThatThrownBy(() -> service.revoke(11L))
                .isInstanceOf(BizException.class)
                .hasMessage("邀请已失效，不能撤销");
    }

    private UserInvitation pendingInvitation(LocalDateTime expiresAt) {
        UserInvitation invitation = new UserInvitation();
        invitation.setId(11L);
        invitation.setTokenHash("hash");
        invitation.setUsername("physics_member");
        invitation.setNickname("物理作者");
        invitation.setStatus("PENDING");
        invitation.setExpiresAt(expiresAt);
        invitation.setCreatedBy(1L);
        invitation.setRoleCode("MEMBER");
        return invitation;
    }
}
