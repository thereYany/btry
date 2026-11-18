package com.wherelee.edu.modules.identity.invitation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TeacherInvitationMapper {

    int insert(TeacherInvitation invitation);

    TeacherInvitation findById(@Param("id") Long id, @Param("tenantId") Long tenantId);

    TeacherInvitation findByTokenHash(@Param("tokenHash") String tokenHash,
                                      @Param("tenantId") Long tenantId);

    TeacherInvitation findByTokenHashForUpdate(@Param("tokenHash") String tokenHash,
                                               @Param("tenantId") Long tenantId);

    TeacherInvitation findPendingByUsername(@Param("username") String username,
                                            @Param("now") LocalDateTime now,
                                            @Param("tenantId") Long tenantId);

    List<TeacherInvitationVO> findPage(@Param("status") String status,
                                       @Param("pageNow") LocalDateTime pageNow,
                                       @Param("offset") int offset,
                                       @Param("size") int size,
                                       @Param("tenantId") Long tenantId);

    long count(@Param("status") String status, @Param("tenantId") Long tenantId);

    int markUsed(@Param("id") Long id,
                 @Param("userId") Long userId,
                 @Param("now") LocalDateTime now,
                 @Param("tenantId") Long tenantId);

    int revoke(@Param("id") Long id, @Param("now") LocalDateTime now,
               @Param("tenantId") Long tenantId);
}
