package com.wherelee.edu.modules.commerce.order;

import com.wherelee.edu.modules.platform.aop.Measured;
import com.wherelee.edu.modules.platform.aop.RateLimit;
import com.wherelee.edu.modules.platform.aop.PermissionCodes;
import com.wherelee.edu.modules.platform.aop.RequirePermission;
import com.wherelee.edu.modules.platform.common.PageResult;
import com.wherelee.edu.modules.contracts.commerce.OrderDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @RequirePermission(PermissionCodes.ORDER_CREATE)
    @RateLimit(key = "order:create", limit = 20, windowSeconds = 60)
    @Measured("order.create")
    public OrderDtos.OrderView create(@RequestAttribute("userId") Long userId,
                                      @Valid @RequestBody OrderDtos.CreateOrderRequest request) {
        return orderService.create(userId, request);
    }

    @GetMapping
    @RequirePermission(PermissionCodes.ORDER_READ)
    @Measured("order.page")
    public PageResult<OrderDtos.OrderView> list(@RequestAttribute("userId") Long userId,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return orderService.list(userId, page, size);
    }

    @GetMapping("/{orderNo}")
    @RequirePermission(PermissionCodes.ORDER_READ)
    @Measured("order.detail")
    public OrderDtos.OrderDetail detail(@RequestAttribute("userId") Long userId,
                                        @PathVariable String orderNo) {
        return orderService.detail(userId, orderNo);
    }

    @PostMapping("/{orderNo}/close")
    @RequirePermission(PermissionCodes.ORDER_CANCEL)
    @RateLimit(key = "order:close", limit = 10, windowSeconds = 60)
    @Measured("order.close")
    public OrderDtos.OrderView close(@RequestAttribute("userId") Long userId,
                                     @PathVariable String orderNo) {
        return orderService.close(userId, orderNo);
    }
}
