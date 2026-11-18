package com.wherelee.template;

import com.wherelee.base.modules.identity.security.JwtService;
import com.wherelee.base.modules.identity.user.User;
import com.wherelee.base.modules.identity.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("integration")
@AutoConfigureMockMvc
class ResourcePackFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;

    private Long userId;
    private String bearer;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM t_resource_pack_grant");
        jdbc.update("DELETE FROM t_resource_pack_order");
        jdbc.update("DELETE FROM t_resource_pack");
        jdbc.update("INSERT INTO t_resource_pack (tenant_id, title, description, price_cents, status) "
                + "VALUES (0, '测试资料包', 'P4.5 演示', 9900, 'ON_SALE')");
        jdbc.update("DELETE FROM t_user_role WHERE user_id = (SELECT id FROM t_user WHERE username = 'template_user')");
        jdbc.update("DELETE FROM t_user WHERE username = 'template_user'");
        jdbc.update("""
                INSERT INTO t_user (username, password_hash, nickname, role, status, must_change_password, token_version)
                VALUES ('template_user', 'not-used', '模板用户', 'ADMIN', 'ACTIVE', FALSE, 0)
                """);
        userId = jdbc.queryForObject("SELECT id FROM t_user WHERE username = 'template_user'", Long.class);
        Long roleId = jdbc.queryForObject("SELECT id FROM t_role WHERE tenant_id = 0 AND code = 'ADMIN'", Long.class);
        jdbc.update("INSERT IGNORE INTO t_user_role (tenant_id, user_id, role_id) VALUES (0, ?, ?)", userId, roleId);
        User user = userRepository.findById(userId).orElseThrow();
        bearer = "Bearer " + jwtService.createToken(user);
    }

    @Test
    void loginToResourcePackPaymentAndFulfillment() throws Exception {
        Long packId = jdbc.queryForObject("SELECT id FROM t_resource_pack LIMIT 1", Long.class);

        mockMvc.perform(get("/api/resource-packs").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("测试资料包"));

        String body = mockMvc.perform(post("/api/resource-packs/{id}/orders", packId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAYING"))
                .andReturn().getResponse().getContentAsString();
        String orderNo = com.jayway.jsonpath.JsonPath.read(body, "$.orderNo");

        mockMvc.perform(post("/api/resource-packs/orders/{orderNo}/mock-pay", orderNo)
                        .header("Authorization", bearer))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "SELECT status FROM t_resource_pack_order WHERE order_no = ?", String.class, orderNo))
                .isEqualTo("PAID");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_resource_pack_grant WHERE user_id = ?", Integer.class, userId))
                .isEqualTo(1);

        mockMvc.perform(get("/api/resource-packs/grants/me").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].packId").value(packId));
    }
}
