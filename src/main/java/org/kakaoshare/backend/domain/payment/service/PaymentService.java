package org.kakaoshare.backend.domain.payment.service;

import lombok.RequiredArgsConstructor;
import org.kakaoshare.backend.common.util.RedisUtils;
import org.kakaoshare.backend.domain.gift.entity.Gift;
import org.kakaoshare.backend.domain.gift.repository.GiftRepository;
import org.kakaoshare.backend.domain.member.entity.Member;
import org.kakaoshare.backend.domain.member.repository.MemberRepository;
import org.kakaoshare.backend.domain.option.dto.OptionSummaryResponse;
import org.kakaoshare.backend.domain.option.repository.OptionDetailRepository;
import org.kakaoshare.backend.domain.order.dto.OrderSummaryResponse;
import org.kakaoshare.backend.domain.order.entity.Order;
import org.kakaoshare.backend.domain.order.repository.OrderRepository;
import org.kakaoshare.backend.domain.payment.dto.OrderDetail;
import org.kakaoshare.backend.domain.payment.dto.OrderDetails;
import org.kakaoshare.backend.domain.payment.dto.approve.response.KakaoPayApproveResponse;
import org.kakaoshare.backend.domain.payment.dto.ready.request.PaymentReadyRequest;
import org.kakaoshare.backend.domain.payment.dto.ready.response.KakaoPayReadyResponse;
import org.kakaoshare.backend.domain.payment.dto.ready.response.PaymentReadyResponse;
import org.kakaoshare.backend.domain.payment.dto.success.request.PaymentSuccessRequest;
import org.kakaoshare.backend.domain.payment.dto.success.response.PaymentSuccessResponse;
import org.kakaoshare.backend.domain.payment.dto.success.response.Receiver;
import org.kakaoshare.backend.domain.payment.entity.Payment;
import org.kakaoshare.backend.domain.payment.exception.PaymentException;
import org.kakaoshare.backend.domain.payment.repository.PaymentRepository;
import org.kakaoshare.backend.domain.product.dto.ProductSummaryResponse;
import org.kakaoshare.backend.domain.product.repository.ProductRepository;
import org.kakaoshare.backend.domain.receipt.entity.Receipt;
import org.kakaoshare.backend.domain.receipt.entity.ReceiptOption;
import org.kakaoshare.backend.domain.receipt.entity.Receipts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.kakaoshare.backend.domain.payment.exception.PaymentErrorCode.INVALID_AMOUNT;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class PaymentService {
    private final GiftRepository giftRepository;
    private final MemberRepository memberRepository;
    private final OptionDetailRepository optionDetailRepository;
    private final OrderRepository orderRepository;
    private final OrderNumberProvider orderNumberProvider;
    private final PaymentRepository paymentRepository;
    private final PaymentWebClientService webClientService;
    private final ProductRepository productRepository;
    private final RedisUtils redisUtils;

    public PaymentReadyResponse ready(final String providerId,
                                      final List<PaymentReadyRequest> paymentReadyRequests) {
        validateTotalAmount(paymentReadyRequests);
        final String orderDetailKey = orderNumberProvider.createOrderDetailKey();
        final OrderDetails orderDetails = getOrderDetails(paymentReadyRequests);
        final KakaoPayReadyResponse kakaoPayReadyResponse = webClientService.ready(providerId, paymentReadyRequests, orderDetailKey);
        redisUtils.save(orderDetailKey, orderDetails);
        return new PaymentReadyResponse(kakaoPayReadyResponse.tid(), kakaoPayReadyResponse.next_redirect_pc_url(), orderDetailKey);
    }

    @Transactional
    public PaymentSuccessResponse approve(final String providerId,
                                          final PaymentSuccessRequest paymentSuccessRequest) {
        final KakaoPayApproveResponse approveResponse = webClientService.approve(providerId, paymentSuccessRequest);
        final Payment payment = saveAndGetPayment(approveResponse);
        final Member recipient = findMemberByProviderId(providerId); // TODO: 3/30/24 토큰에 저장된 값이 PK가 아니라 Member 엔티티를 가져와야 함
        final Member receiver = findMemberByProviderId(providerId); // TODO: 3/28/24 친구목록이 구현되지 않아 나에게로 선물만 구현
        final OrderDetails orderDetails = redisUtils.remove(paymentSuccessRequest.orderNumber(), OrderDetails.class);
        final Receipts receipts = getReceipts(recipient.getMemberId(), receiver, orderDetails);
        saveGifts(receipts);
        saveOrders(payment, receipts);
        final List<OrderSummaryResponse> orderSummaries = getOrderSummaries(orderDetails);
        return new PaymentSuccessResponse(Receiver.from(receiver), orderSummaries);
    }

    private void validateTotalAmount(final List<PaymentReadyRequest> paymentReadyRequests) {
        final List<Long> productIds = extractedProductIds(paymentReadyRequests, PaymentReadyRequest::productId);
        final Map<Long, Long> priceByIds = productRepository.findAllPriceByIdsGroupById(productIds);
        final boolean isAllMatch = paymentReadyRequests.stream()
                .anyMatch(paymentReadyRequest -> paymentReadyRequest.stockQuantity() * priceByIds.get(paymentReadyRequest.productId()) != paymentReadyRequest.totalAmount());
        if (!isAllMatch) {
            throw new PaymentException(INVALID_AMOUNT);
        }
    }

    private OrderDetails getOrderDetails(final List<PaymentReadyRequest> paymentReadyRequests) {
        final List<OrderDetail> orderDetails = paymentReadyRequests.stream()
                .map(paymentReadyRequest -> paymentReadyRequest.toOrderDetail(orderNumberProvider.createOrderNumber()))
                .toList();
        return new OrderDetails(orderDetails);
    }

    private Payment saveAndGetPayment(final KakaoPayApproveResponse approveResponse) {
        final Payment payment = approveResponse.toEntity();
        return paymentRepository.save(payment);
    }

    private Receipts getReceipts(final Long recipientId,
                                 final Member receiver,
                                 final OrderDetails orderDetails) {
        final Member recipient = memberRepository.getReferenceById(recipientId);
        final List<Receipt> receipts = orderDetails.getValues()
                .stream()
                .map(orderDetail -> new Receipt(
                        orderDetail.orderNumber(),
                        productRepository.getReferenceById(orderDetail.productId()),
                        orderDetail.stockQuantity(),
                        recipient,
                        receiver,
                        getReceiptOptions(orderDetail.optionDetailIds())))
                .toList();
        return new Receipts(receipts);
    }

    private List<ReceiptOption> getReceiptOptions(final List<Long> optionDetailIds) {
        if (optionDetailIds == null || optionDetailIds.isEmpty()) {
            return Collections.emptyList();
        }

        return optionDetailRepository.findAllById(optionDetailIds)
                .stream()
                .map(optionDetail -> new ReceiptOption(optionDetail.getOption().getName(), optionDetail.getName()))
                .toList();
    }

    private void saveOrders(final Payment payment, final Receipts receipts) {
        final List<Order> orders = receipts.toOrders(payment);
        orderRepository.saveAll(orders);
    }

    private void saveGifts(final Receipts receipts) {
        final List<Gift> gifts = receipts.toGifts(LocalDateTime.now().plusDays(180L));    // TODO: 3/29/24 선물 만료기간은 180일로 설정
        giftRepository.saveAll(gifts);
    }

    private List<OrderSummaryResponse> getOrderSummaries(final OrderDetails orderDetails) {
        return orderDetails.getValues()
                .stream()
                .map(this::getOrderSummary)
                .toList();
    }

    private OrderSummaryResponse getOrderSummary(final OrderDetail orderDetail) {
        final ProductSummaryResponse productSummaryResponse = productRepository.findAllProductSummaryById(orderDetail.productId());
        return new OrderSummaryResponse(productSummaryResponse, orderDetail.stockQuantity(), getOptionSummaryResponses(orderDetail.optionDetailIds()));
    }

    private List<OptionSummaryResponse> getOptionSummaryResponses(final List<Long> optionDetailIds) {
        return optionDetailRepository.findAllById(optionDetailIds)
                .stream()
                .map(OptionSummaryResponse::from)
                .toList();
    }

    private <T> List<Long> extractedProductIds(final List<T> values, final Function<T, Long> mapper) {
        return values.stream()
                .map(mapper)
                .toList();
    }

    private Member findMemberByProviderId(final String providerId) {
        return memberRepository.findByProviderId(providerId)
                .orElseThrow(IllegalArgumentException::new);
    }
}
