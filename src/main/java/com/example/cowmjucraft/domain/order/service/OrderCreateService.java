package com.example.cowmjucraft.domain.order.service;

import com.example.cowmjucraft.domain.item.entity.ItemSaleType;
import com.example.cowmjucraft.domain.item.entity.ItemStatus;
import com.example.cowmjucraft.domain.item.entity.ProjectItem;
import com.example.cowmjucraft.domain.item.repository.ProjectItemRepository;
import com.example.cowmjucraft.domain.order.dto.request.OrderCreateBuyerRequestDto;
import com.example.cowmjucraft.domain.order.dto.request.OrderCreateFulfillmentRequestDto;
import com.example.cowmjucraft.domain.order.dto.request.OrderCreateItemRequestDto;
import com.example.cowmjucraft.domain.order.dto.request.OrderCreateRequestDto;
import com.example.cowmjucraft.domain.order.dto.response.OrderCreateResponseDto;
import com.example.cowmjucraft.domain.order.entity.Order;
import com.example.cowmjucraft.domain.order.entity.OrderAuth;
import com.example.cowmjucraft.domain.order.entity.OrderBuyer;
import com.example.cowmjucraft.domain.order.entity.OrderFulfillment;
import com.example.cowmjucraft.domain.order.entity.OrderFulfillmentMethod;
import com.example.cowmjucraft.domain.order.entity.OrderItem;
import com.example.cowmjucraft.domain.order.entity.OrderStatus;
import com.example.cowmjucraft.domain.order.exception.OrderErrorType;
import com.example.cowmjucraft.domain.order.exception.OrderException;
import com.example.cowmjucraft.domain.order.repository.OrderAuthRepository;
import com.example.cowmjucraft.domain.order.repository.OrderBuyerRepository;
import com.example.cowmjucraft.domain.order.repository.OrderFulfillmentRepository;
import com.example.cowmjucraft.domain.order.repository.OrderItemRepository;
import com.example.cowmjucraft.domain.order.repository.OrderRepository;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderCreateService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DateTimeFormatter ORDER_NO_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderBuyerRepository orderBuyerRepository;
    private final OrderFulfillmentRepository orderFulfillmentRepository;
    private final OrderAuthRepository orderAuthRepository;
    private final ProjectItemRepository projectItemRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrderViewTokenService orderViewTokenService;
    private final EmailService emailService;

    @Transactional
    public OrderCreateResponseDto createOrder(OrderCreateRequestDto request) {
        validateAgreements(request);

        String depositorName = normalizeRequiredText(request.depositorName(), "입금자명");
        String lookupId = normalizeRequiredText(request.lookupId(), "조회 아이디");
        String password = normalizeRequiredText(request.password(), "조회 비밀번호");

        if (orderAuthRepository.existsByLookupId(lookupId)) {
            throw new OrderException(OrderErrorType.DUPLICATED_LOOKUP_ID);
        }

        Map<Long, Integer> quantityByItemId = aggregateItemQuantities(request.items());

        int totalAmount = 0;
        List<ResolvedOrderLine> lines = new ArrayList<>();

        for (Map.Entry<Long, Integer> entry : quantityByItemId.entrySet()) {
            Long projectItemId = entry.getKey();
            int quantity = entry.getValue();

            ProjectItem projectItem = projectItemRepository.findById(projectItemId)
                    .orElseThrow(() -> new OrderException(
                            OrderErrorType.ITEM_NOT_FOUND,
                            "projectItemId=" + projectItemId
                    ));

            if (projectItem.getStatus() != ItemStatus.OPEN) {
                throw new OrderException(OrderErrorType.ITEM_NOT_AVAILABLE, "projectItemId=" + projectItemId);
            }

            validateOrderableQuantity(projectItem, quantity);

            int unitPrice = projectItem.getPrice();
            int lineAmount;
            try {
                lineAmount = Math.multiplyExact(unitPrice, quantity);
                totalAmount = Math.addExact(totalAmount, lineAmount);
            } catch (ArithmeticException exception) {
                throw new OrderException(OrderErrorType.ORDER_AMOUNT_OVERFLOW);
            }

            lines.add(new ResolvedOrderLine(projectItem, quantity, unitPrice, lineAmount));
        }

        int shippingFee = 0;
        int finalAmount;
        try {
            finalAmount = Math.addExact(totalAmount, shippingFee);
        } catch (ArithmeticException exception) {
            throw new OrderException(OrderErrorType.ORDER_AMOUNT_OVERFLOW);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        boolean privacyAgreed = true;
        boolean refundAgreed = true;
        boolean cancelRiskAgreed = true;
        Order order = new Order(
                generateOrderNo(now),
                OrderStatus.PENDING_DEPOSIT,
                totalAmount,
                shippingFee,
                finalAmount,
                today.plusDays(1).atTime(23, 59, 59),
                depositorName,
                privacyAgreed,
                now,
                refundAgreed,
                now,
                cancelRiskAgreed,
                now
        );

        Order savedOrder = orderRepository.save(order);

        List<OrderItem> orderItems = lines.stream()
                .map(line -> new OrderItem(
                        savedOrder,
                        line.projectItem(),
                        line.quantity(),
                        line.unitPrice(),
                        line.lineAmount(),
                        line.projectItem().getName()
                ))
                .toList();
        orderItemRepository.saveAll(orderItems);

        OrderCreateBuyerRequestDto buyer = request.buyer();
        orderBuyerRepository.save(new OrderBuyer(
                savedOrder,
                buyer.buyerType(),
                trimToNull(buyer.campus()),
                normalizeRequiredText(buyer.name(), "주문자 이름"),
                trimToNull(buyer.departmentOrMajor()),
                trimToNull(buyer.studentNo()),
                normalizeRequiredText(buyer.phone(), "주문자 연락처"),
                normalizeRequiredText(buyer.refundBank(), "환불 은행"),
                normalizeRequiredText(buyer.refundAccount(), "환불 계좌"),
                trimToNull(buyer.referralSource()),
                normalizeRequiredText(buyer.email(), "이메일")
        ));

        OrderCreateFulfillmentRequestDto fulfillment = request.fulfillment();
        String postalCode = trimToNull(fulfillment.postalCode());
        String addressLine1 = trimToNull(fulfillment.addressLine1());
        String addressLine2 = trimToNull(fulfillment.addressLine2());
        if (fulfillment.method() == OrderFulfillmentMethod.DELIVERY
                && (postalCode == null || addressLine1 == null)) {
            throw new OrderException(OrderErrorType.DELIVERY_ADDRESS_REQUIRED);
        }
        orderFulfillmentRepository.save(new OrderFulfillment(
                savedOrder,
                fulfillment.method(),
                normalizeRequiredText(fulfillment.receiverName(), "수령인 이름"),
                normalizeRequiredText(fulfillment.receiverPhone(), "수령인 연락처"),
                Boolean.TRUE.equals(fulfillment.infoConfirmed()),
                postalCode,
                addressLine1,
                addressLine2,
                trimToNull(fulfillment.deliveryMemo())
        ));

        orderAuthRepository.save(new OrderAuth(
                savedOrder,
                lookupId,
                passwordEncoder.encode(password)
        ));

        String rawViewToken = orderViewTokenService.issueNewToken(savedOrder, now);

        OrderCreateBuyerRequestDto buyerForMail = request.buyer();
        String viewUrl = orderViewTokenService.buildOrderViewUrl(rawViewToken);
        emailService.sendOrderViewLink(
                buyerForMail.email(),
                buyerForMail.name(),
                savedOrder.getOrderNo(),
                viewUrl,
                savedOrder.getDepositDeadline()
        );

        return new OrderCreateResponseDto(
                savedOrder.getId(),
                savedOrder.getOrderNo(),
                savedOrder.getStatus().name(),
                savedOrder.getTotalAmount(),
                savedOrder.getShippingFee(),
                savedOrder.getFinalAmount(),
                savedOrder.getDepositDeadline(),
                lookupId,
                rawViewToken
        );
    }

    private void validateAgreements(OrderCreateRequestDto request) {
        if (!Boolean.TRUE.equals(request.privacyAgreed())) {
            throw new OrderException(OrderErrorType.PRIVACY_AGREEMENT_REQUIRED);
        }
        if (!Boolean.TRUE.equals(request.refundAgreed())) {
            throw new OrderException(OrderErrorType.REFUND_AGREEMENT_REQUIRED);
        }
        if (!Boolean.TRUE.equals(request.cancelRiskAgreed())) {
            throw new OrderException(OrderErrorType.CANCEL_RISK_AGREEMENT_REQUIRED);
        }
    }

    private Map<Long, Integer> aggregateItemQuantities(List<OrderCreateItemRequestDto> items) {
        if (items == null || items.isEmpty()) {
            throw new OrderException(OrderErrorType.ORDER_ITEMS_REQUIRED);
        }

        Map<Long, Integer> quantityByItemId = new LinkedHashMap<>();
        for (OrderCreateItemRequestDto item : items) {
            if (item == null || item.projectItemId() == null) {
                throw new OrderException(OrderErrorType.INVALID_ORDER_ITEM);
            }
            if (item.quantity() <= 0) {
                throw new OrderException(OrderErrorType.QUANTITY_MUST_BE_POSITIVE);
            }
            quantityByItemId.merge(item.projectItemId(), item.quantity(), Math::addExact);
        }
        return quantityByItemId;
    }

    private void validateOrderableQuantity(ProjectItem projectItem, int quantity) {
        if (projectItem.getSaleType() == ItemSaleType.NORMAL) {
            Integer stockQty = projectItem.getStockQty();
            if (stockQty == null || stockQty < quantity) {
                throw new OrderException(OrderErrorType.INSUFFICIENT_STOCK, "projectItemId=" + projectItem.getId());
            }
            return;
        }

        if (projectItem.getSaleType() == ItemSaleType.GROUPBUY) {
            return;
        }

        throw new OrderException(OrderErrorType.SALE_TYPE_NOT_ORDERABLE, "projectItemId=" + projectItem.getId());
    }

    private String generateOrderNo(LocalDateTime now) {
        for (int i = 0; i < 20; i++) {
            String candidate = "ORD-" + now.format(ORDER_NO_TIME_FORMAT)
                    + "-" + String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
            if (!orderRepository.existsByOrderNo(candidate)) {
                return candidate;
            }
        }
        throw new OrderException(OrderErrorType.ORDER_NO_GENERATION_FAILED);
    }

    private String normalizeRequiredText(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new OrderException(OrderErrorType.REQUIRED_FIELD_MISSING, fieldName + "은(는) 필수입니다.");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ResolvedOrderLine(ProjectItem projectItem, int quantity, int unitPrice, int lineAmount) {
    }
}
