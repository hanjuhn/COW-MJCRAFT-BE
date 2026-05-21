package com.example.cowmjucraft.domain.order.service;

import com.example.cowmjucraft.domain.item.entity.ItemSaleType;
import com.example.cowmjucraft.domain.item.entity.ItemStatus;
import com.example.cowmjucraft.domain.item.entity.ItemType;
import com.example.cowmjucraft.domain.item.entity.ProjectItem;
import com.example.cowmjucraft.domain.item.repository.ProjectItemRepository;
import com.example.cowmjucraft.domain.order.dto.request.OrderCreateBuyerRequestDto;
import com.example.cowmjucraft.domain.order.dto.request.OrderCreateFulfillmentRequestDto;
import com.example.cowmjucraft.domain.order.dto.request.OrderCreateItemRequestDto;
import com.example.cowmjucraft.domain.order.dto.request.OrderCreateRequestDto;
import com.example.cowmjucraft.domain.order.entity.Order;
import com.example.cowmjucraft.domain.order.entity.OrderBuyerType;
import com.example.cowmjucraft.domain.order.entity.OrderFulfillmentMethod;
import com.example.cowmjucraft.domain.order.repository.OrderAuthRepository;
import com.example.cowmjucraft.domain.order.repository.OrderBuyerRepository;
import com.example.cowmjucraft.domain.order.repository.OrderFulfillmentRepository;
import com.example.cowmjucraft.domain.order.repository.OrderItemRepository;
import com.example.cowmjucraft.domain.order.repository.OrderRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCreateServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private OrderBuyerRepository orderBuyerRepository;
    @Mock
    private OrderFulfillmentRepository orderFulfillmentRepository;
    @Mock
    private OrderAuthRepository orderAuthRepository;
    @Mock
    private ProjectItemRepository projectItemRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private OrderViewTokenService orderViewTokenService;
    @Mock
    private EmailService emailService;

    private OrderCreateService orderCreateService;

    @BeforeEach
    void setUp() {
        orderCreateService = new OrderCreateService(
                orderRepository,
                orderItemRepository,
                orderBuyerRepository,
                orderFulfillmentRepository,
                orderAuthRepository,
                projectItemRepository,
                passwordEncoder,
                orderViewTokenService,
                emailService
        );
    }

    @Test
    void createOrder_allowsGroupbuyItemWithinRemainingQuantity() {
        ProjectItem item = groupbuyItem(1L, 100, 40);
        when(orderAuthRepository.existsByLookupId("guest-mju-001")).thenReturn(false);
        when(projectItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(orderRepository.existsByOrderNo(any())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 10L);
            return order;
        });
        when(passwordEncoder.encode("Pa$$w0rd!")).thenReturn("encoded-password");
        when(orderViewTokenService.issueNewToken(any(Order.class), any())).thenReturn("raw-token");
        when(orderViewTokenService.buildOrderViewUrl("raw-token")).thenReturn("https://example.com/orders/view?token=raw-token");

        orderCreateService.createOrder(request(60));

        verify(orderItemRepository).saveAll(any());
    }

    @Test
    void createOrder_allowsGroupbuyItemOverRemainingQuantity() {
        ProjectItem item = groupbuyItem(1L, 100, 40);
        when(orderAuthRepository.existsByLookupId("guest-mju-001")).thenReturn(false);
        when(projectItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(orderRepository.existsByOrderNo(any())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 10L);
            return order;
        });
        when(passwordEncoder.encode("Pa$$w0rd!")).thenReturn("encoded-password");
        when(orderViewTokenService.issueNewToken(any(Order.class), any())).thenReturn("raw-token");
        when(orderViewTokenService.buildOrderViewUrl("raw-token")).thenReturn("https://example.com/orders/view?token=raw-token");

        orderCreateService.createOrder(request(61));

        verify(orderItemRepository).saveAll(any());
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

    private OrderCreateRequestDto request(int quantity) {
        return new OrderCreateRequestDto(
                "guest-mju-001",
                "Pa$$w0rd!",
                "홍길동",
                true,
                true,
                true,
                List.of(new OrderCreateItemRequestDto(1L, quantity)),
                new OrderCreateBuyerRequestDto(
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
                ),
                new OrderCreateFulfillmentRequestDto(
                        OrderFulfillmentMethod.PICKUP,
                        "홍길동",
                        "010-1234-5678",
                        true,
                        null,
                        null,
                        null,
                        null
                )
        );
    }
}
