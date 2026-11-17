package com.wherelee.edu.modules.operations.admin;

import com.wherelee.edu.modules.commerce.api.AdminOrderOperations;
import com.wherelee.edu.modules.identity.api.AccountDirectory;
import com.wherelee.edu.modules.identity.api.AccountQuery.AccountProfile;
import com.wherelee.edu.modules.commerce.payment.PaymentDtos;
import com.wherelee.edu.modules.platform.common.BizException;
import com.wherelee.edu.modules.platform.common.PageResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AdminOrderService {

    private static final int MAX_PAGE_SIZE = 50;

    private final AdminOrderMapper mapper;
    private final AdminOrderOperations orderOperations;
    private final AccountDirectory accountDirectory;

    public AdminOrderService(AdminOrderMapper mapper,
                             AdminOrderOperations orderOperations,
                             AccountDirectory accountDirectory) {
        this.mapper = mapper;
        this.orderOperations = orderOperations;
        this.accountDirectory = accountDirectory;
    }

    public PageResult<AdminOrderVO> page(String keyword, String status, int page, int size) {
        int safePage = Math.min(Math.max(page, 1), 10_000);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String normalizedKeyword = blankToNull(keyword);
        // 按用户名/昵称搜索在身份域完成，订单表只按 userId 集合过滤
        List<Long> userIds = normalizedKeyword == null
                ? null : accountDirectory.searchIds(normalizedKeyword, 200);
        long total = mapper.count(normalizedKeyword, blankToNull(status), userIds);
        if (total == 0) {
            return new PageResult<>(List.of(), 0, safePage, safeSize);
        }
        List<AdminOrderVO> records = mapper.findPage(normalizedKeyword, blankToNull(status), userIds,
                (safePage - 1) * safeSize, safeSize);
        enrich(records);
        return new PageResult<>(records, total, safePage, safeSize);
    }

    public PageResult<AdminOrderVO> exceptions(int page, int size) {
        int safePage = Math.min(Math.max(page, 1), 10_000);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long total = mapper.countExceptions();
        if (total == 0) {
            return new PageResult<>(List.of(), 0, safePage, safeSize);
        }
        List<AdminOrderVO> records = mapper.findExceptions((safePage - 1) * safeSize, safeSize);
        enrich(records);
        return new PageResult<>(records, total, safePage, safeSize);
    }

    public AdminOrderDtos.Detail detail(String orderNo) {
        AdminOrderVO row = mapper.findOne(orderNo);
        if (row == null) {
            throw BizException.notFound("订单不存在");
        }
        enrich(List.of(row));
        AdminOrderOperations.Snapshot snapshot = orderOperations.snapshot(orderNo);
        return new AdminOrderDtos.Detail(
                row,
                snapshot.orderDetail(),
                snapshot.attempts(),
                snapshot.events(),
                snapshot.refund()
        );
    }

    public AdminOrderDtos.Detail query(String orderNo) {
        orderOperations.queryNow(orderNo);
        return detail(orderNo);
    }

    public AdminOrderDtos.Detail fulfill(String orderNo) {
        orderOperations.fulfill(orderNo);
        return detail(orderNo);
    }

    public AdminOrderDtos.Detail close(String orderNo) {
        orderOperations.close(orderNo);
        return detail(orderNo);
    }

    public AdminOrderDtos.Detail refund(String orderNo, Long operatorId,
                                        PaymentDtos.RefundRequest request) {
        orderOperations.refund(orderNo, operatorId, request.reason(), request.simulateFailure());
        return detail(orderNo);
    }

    /** 下单人展示信息来自身份域契约，订单库不再 join 用户表。 */
    private void enrich(List<AdminOrderVO> rows) {
        if (rows.isEmpty()) {
            return;
        }
        Map<Long, AccountProfile> profiles = accountDirectory.profiles(
                rows.stream().map(AdminOrderVO::getUserId).toList());
        for (AdminOrderVO row : rows) {
            AccountProfile profile = profiles.get(row.getUserId());
            if (profile != null) {
                row.setUsername(profile.username());
                row.setNickname(profile.nickname());
            }
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}