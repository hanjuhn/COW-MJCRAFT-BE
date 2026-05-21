package com.example.cowmjucraft.domain.order.service;

import com.example.cowmjucraft.domain.item.entity.ItemSaleType;
import com.example.cowmjucraft.domain.item.entity.ProjectItem;
import com.example.cowmjucraft.domain.item.repository.ProjectItemRepository;
import com.example.cowmjucraft.domain.order.dto.response.AdminOrderStatusResponseDto;
import com.example.cowmjucraft.domain.order.entity.Order;
import com.example.cowmjucraft.domain.order.entity.OrderBuyer;
import com.example.cowmjucraft.domain.order.entity.OrderItem;
import com.example.cowmjucraft.domain.order.entity.OrderStatus;
import com.example.cowmjucraft.domain.order.exception.OrderErrorType;
import com.example.cowmjucraft.domain.order.exception.OrderException;
import com.example.cowmjucraft.domain.order.repository.OrderBuyerRepository;
import com.example.cowmjucraft.domain.order.repository.OrderItemRepository;
import com.example.cowmjucraft.domain.order.repository.OrderRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminOrderPaymentService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProjectItemRepository projectItemRepository;
    private final OrderBuyerRepository orderBuyerRepository;
    private final OrderViewTokenService orderViewTokenService;
    private final EmailService emailService;

    @Transactional
    public AdminOrderStatusResponseDto confirmPaid(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorType.ORDER_NOT_FOUND, "orderId=" + orderId));

        OrderStatusTransitionPolicy.validate(order.getStatus(), OrderStatus.PAID);

        if (order.getStockDeductedAt() != null) {
            throw new OrderException(OrderErrorType.ALREADY_STOCK_DEDUCTED, "orderId=" + orderId);
        }

        List<OrderItem> orderItems = orderItemRepository.findAllByOrderIdOrderByProjectItemIdAsc(orderId);
        if (orderItems.isEmpty()) {
            throw new OrderException(OrderErrorType.ORDER_ITEMS_EMPTY, "orderId=" + orderId);
        }

        for (OrderItem orderItem : orderItems) {
            ProjectItem projectItem = projectItemRepository.findByIdForUpdate(orderItem.getProjectItem().getId())
                    .orElseThrow(() -> new OrderException(
                            OrderErrorType.ITEM_NOT_FOUND,
                            "projectItemId=" + orderItem.getProjectItem().getId()
                    ));

            applyPaidQuantity(projectItem, orderItem.getQuantity());
        }

        LocalDateTime now = LocalDateTime.now();
        order.confirmPaid(now, now);

        OrderBuyer buyer = orderBuyerRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorType.BUYER_NOT_FOUND, "orderId=" + orderId));

        String rawToken = orderViewTokenService.rotateToken(order, now);
        String viewUrl = orderViewTokenService.buildOrderViewUrl(rawToken);
        emailService.sendPaidConfirmed(
                buyer.getEmail(),
                buyer.getName(),
                order.getOrderNo(),
                viewUrl,
                now
        );

        return new AdminOrderStatusResponseDto(order.getId(), order.getStatus().name());
    }

    private void applyPaidQuantity(ProjectItem projectItem, int orderQty) {
        if (projectItem.getSaleType() == ItemSaleType.NORMAL) {
            Integer stockQty = projectItem.getStockQty();
            if (stockQty == null) {
                throw new OrderException(OrderErrorType.STOCK_INFO_MISSING, "projectItemId=" + projectItem.getId());
            }
            if (stockQty < orderQty) {
                throw new OrderException(OrderErrorType.INSUFFICIENT_STOCK, "projectItemId=" + projectItem.getId());
            }
            projectItem.updateStockQty(stockQty - orderQty);
            return;
        }

        if (projectItem.getSaleType() == ItemSaleType.GROUPBUY) {
            int fundedQty = projectItem.getFundedQty() == null ? 0 : projectItem.getFundedQty();
            projectItem.updateFundedQty(fundedQty + orderQty);
            return;
        }

        throw new OrderException(OrderErrorType.SALE_TYPE_NOT_ORDERABLE, "projectItemId=" + projectItem.getId());
    }
}
