package com.example.cowmjucraft.domain.order.service;

import com.example.cowmjucraft.domain.item.entity.ItemSaleType;
import com.example.cowmjucraft.domain.item.entity.ItemStatus;
import com.example.cowmjucraft.domain.item.entity.ItemType;
import com.example.cowmjucraft.domain.item.entity.ProjectItem;
import com.example.cowmjucraft.domain.item.repository.ProjectItemRepository;
import com.example.cowmjucraft.domain.order.entity.Order;
import com.example.cowmjucraft.domain.order.entity.OrderBuyer;
import com.example.cowmjucraft.domain.order.entity.OrderBuyerType;
import com.example.cowmjucraft.domain.order.entity.OrderItem;
import com.example.cowmjucraft.domain.order.entity.OrderStatus;
import com.example.cowmjucraft.domain.order.repository.OrderBuyerRepository;
import com.example.cowmjucraft.domain.order.repository.OrderItemRepository;
import com.example.cowmjucraft.domain.order.repository.OrderRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOrderPaymentServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private ProjectItemRepository projectItemRepository;
    @Mock
    private OrderBuyerRepository orderBuyerRepository;
    @Mock
    private OrderViewTokenService orderViewTokenService;
    @Mock
    private EmailService emailService;

    private AdminOrderPaymentService adminOrderPaymentService;

    @BeforeEach
    void setUp() {
        adminOrderPaymentService = new AdminOrderPaymentService(
                orderRepository,
                orderItemRepository,
                projectItemRepository,
                orderBuyerRepository,
                orderViewTokenService,
                emailService
        );
    }

    @Test
    void confirmPaid_increasesGroupbuyFundedQuantity() {
        Order order = order();
        ProjectItem item = groupbuyItem(1L, 100, 40);
        OrderItem orderItem = new OrderItem(order, item, 3, 10_000, 30_000, "공동구매 상품");
        OrderBuyer buyer = buyer(order);

        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderIdOrderByProjectItemIdAsc(10L)).thenReturn(List.of(orderItem));
        when(projectItemRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(item));
        when(orderBuyerRepository.findById(10L)).thenReturn(Optional.of(buyer));
        when(orderViewTokenService.rotateToken(any(Order.class), any())).thenReturn("raw-token");
        when(orderViewTokenService.buildOrderViewUrl("raw-token")).thenReturn("https://example.com/orders/view?token=raw-token");

        adminOrderPaymentService.confirmPaid(10L);

        assertThat(item.getFundedQty()).isEqualTo(43);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void confirmPaid_allowsGroupbuyFundedQuantityOverTargetQuantity() {
        Order order = order();
        ProjectItem item = groupbuyItem(1L, 100, 98);
        OrderItem orderItem = new OrderItem(order, item, 3, 10_000, 30_000, "공동구매 상품");
        OrderBuyer buyer = buyer(order);

        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderIdOrderByProjectItemIdAsc(10L)).thenReturn(List.of(orderItem));
        when(projectItemRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(item));
        when(orderBuyerRepository.findById(10L)).thenReturn(Optional.of(buyer));
        when(orderViewTokenService.rotateToken(any(Order.class), any())).thenReturn("raw-token");
        when(orderViewTokenService.buildOrderViewUrl("raw-token")).thenReturn("https://example.com/orders/view?token=raw-token");

        adminOrderPaymentService.confirmPaid(10L);

        assertThat(item.getFundedQty()).isEqualTo(101);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    private Order order() {
        Order order = new Order(
                "ORD-20260505120000-123456",
                OrderStatus.PENDING_DEPOSIT,
                30_000,
                0,
                30_000,
                LocalDateTime.now().plusDays(1),
                "홍길동",
                true,
                LocalDateTime.now(),
                true,
                LocalDateTime.now(),
                true,
                LocalDateTime.now()
        );
        ReflectionTestUtils.setField(order, "id", 10L);
        return order;
    }

    private ProjectItem groupbuyItem(Long id, int targetQty, int fundedQty) {
        ProjectItem item = new ProjectItem(
                null,
                "공동구매 상품",
                "summary",
                "description",
                10_000,
                ItemSaleType.GROUPBUY,
                ItemStatus.OPEN,
                ItemType.PHYSICAL,
                "thumb.png",
                null,
                targetQty,
                fundedQty,
                null
        );
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private OrderBuyer buyer(Order order) {
        return new OrderBuyer(
                order,
                OrderBuyerType.STUDENT,
                "SEOUL",
                "홍길동",
                "컴퓨터공학과",
                "60123456",
                "010-1234-5678",
                "국민은행",
                "123456-78-901234",
                "instagram",
                "hong@example.com"
        );
    }
}
