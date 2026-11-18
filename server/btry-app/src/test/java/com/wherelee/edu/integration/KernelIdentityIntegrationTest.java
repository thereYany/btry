package com.wherelee.edu.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 内核证据测试：登录 → 权限码鉴权 → 运营端接口，全程只有内核数据。
 */
@AutoConfigureMockMvc
class KernelIdentityIntegrationTest extends KernelIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("管理员登录后可访问运营端接口，普通角色被权限码拦截")
    void loginAndPermissionCodeGate() throws Exception {
        seedUser("it_admin", "Integration1Pass", "集成管理员", "ADMIN");
        Long memberId = seedUser("it_member", "Integration1Pass", "集成成员", "ADMIN");

        String adminToken = login("it_admin");
        String memberToken = login("it_member");

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records").isArray());

        // 用管理员创建一个没有任何内核权限的自定义角色，赋给第二个账号
        mockMvc.perform(post("/api/admin/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"AUDITOR","name":"审计员","description":"只读审计","permissions":[]}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/users/" + memberId + "/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles":["AUDITOR"]}
                                """))
                .andExpect(status().isOk());

        String auditorToken = login("it_member");
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isForbidden());
    }

    private String login(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Integration1Pass"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(body).get("token").asText();
    }
}
