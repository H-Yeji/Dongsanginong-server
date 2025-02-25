package org.samtuap.inong.domain.subscription.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.samtuap.inong.common.client.ProductFeign;
import org.samtuap.inong.common.exception.BaseCustomException;
import org.samtuap.inong.domain.member.dto.*;
import org.samtuap.inong.domain.member.entity.Member;
import org.samtuap.inong.domain.member.repository.MemberRepository;
import org.samtuap.inong.domain.notification.dto.KafkaNotificationRequest;
import org.samtuap.inong.domain.subscription.dto.*;
import org.samtuap.inong.domain.subscription.entity.Subscription;
import org.samtuap.inong.domain.subscription.repository.SubscriptionRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.samtuap.inong.common.exceptionType.SubscriptionExceptionType.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class SubscriptionService {
    private final MemberRepository memberRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ProductFeign productFeign;
    private final KafkaTemplate<String, Object> kafkaTemplate;


    @Transactional
    public void registerPaymentMethod(Long memberId, BillingKeyRegisterRequest request) {
        Member member = memberRepository.findByIdOrThrow(memberId);
        member.updatePaymentMethod(request.paymentMethodType(), request.billingKey());

        // 알림 발송
        KafkaNotificationRequest notification = KafkaNotificationRequest.builder()
                .memberId(memberId)
                .title("정기 결제 수단이 변경되었어요.")
                .content("본인이 바꾸신 게 아니라면 즉시 고객센터로 문의 바랍니다.")
                .url("/member/subscribe-management")
                .build();
        kafkaTemplate.send("member-notification-topic", notification);
    }


    @Transactional
    public SubscriptionListGetResponse getSubscriptionToPay() {
        List<Subscription> subscriptions = subscriptionRepository.findAllByPayDate(LocalDate.now());

        List<Long> packageProductIds = subscriptions.stream()
                .map(Subscription::getId)
                .toList();

        List<SubscriptionListGetResponse.SubscriptionGetResponse> list = subscriptions.stream()
                .map(SubscriptionListGetResponse.SubscriptionGetResponse::fromEntity)
                .toList();

        // 다음 결제일 변경
        updatePayDates(subscriptions);

        return new SubscriptionListGetResponse(list);
    }

    private void updatePayDates(List<Subscription> subscriptions) {
        subscriptions.forEach(s -> s.updatePayDate(s.getPayDate().plusDays(28)));
    }


    public MemberSubscriptionResponse getSubscription(Long memberId) {
        Member member = memberRepository.findByIdOrThrow(memberId);
        Subscription subscription = subscriptionRepository.findByMemberOrThrow(member);
        Long packageProductId = subscription.getPackageId();
        PackageProductResponse packageProduct = productFeign.getPackageProduct(packageProductId);

        return MemberSubscriptionResponse.fromEntity(packageProduct);
    }

    public List<MemberSubscriptionListResponse> getSubscriptionList(Long memberId) {
        Member member = memberRepository.findByIdOrThrow(memberId);
        List<Subscription> subscriptions = subscriptionRepository.findAllByMember(member);
        // subid와 pacakgeId를 map 형태로 찾아 받아옴
        Map<Long, Long> packageIdToSubscriptionId = subscriptions.stream()
                .collect(Collectors.toMap(Subscription::getPackageId, Subscription::getId));

        List<PackageProductSubsResponse> subscriptionList = productFeign.getProductSubsList(new ArrayList<>(packageIdToSubscriptionId.keySet()));
        return subscriptionList.stream()
                .map(subscriptionProductList -> MemberSubscriptionListResponse.builder()
                        .id(packageIdToSubscriptionId.get(subscriptionProductList.packageId())) // packageProductId에 맞는 subId를 할당
                        .packageId(subscriptionProductList.packageId())
                        .packageName(subscriptionProductList.packageName())
                        .imageUrl(subscriptionProductList.imageUrl())
                        .farmId(subscriptionProductList.farmId())
                        .farmName(subscriptionProductList.farmName())
                        .build())
                .toList();
    }

    public MemberSubsCancelRequest cancelSubscription(Long memberId, Long subsId) {
        Member member = memberRepository.findByIdOrThrow(memberId);
        Subscription subscription = subscriptionRepository.findByIdOrThrow(subsId);
        PackageProductResponse cancelPackage = productFeign.getPackageProduct(subscription.getPackageId());

        if(!subscription.getMember().getId().equals(memberId)) {
            throw new BaseCustomException(FORBIDDEN);
        }

        subscriptionRepository.delete(subscription);
        return MemberSubsCancelRequest.from(cancelPackage, subscription.getId());
    }

    //== Kafka를 통한 정기 구독 비동기 처리 ==//
    @Transactional
    @KafkaListener(topics = "subscription-topic", groupId = "member-group",/*order group으로 부터 메시지가 들어오면*/ containerFactory = "kafkaListenerContainerFactory")
    public void consumeIssueNotification(String message /*listen 하면 스트링 형태로 메시지가 들어온다*/) {
        ObjectMapper objectMapper = new ObjectMapper();
        KafkaSubscribeProductRequest subscribeRequest = null;
        try {
            subscribeRequest = objectMapper.readValue(message, KafkaSubscribeProductRequest.class);

            SubscribeProductRequest convertedRequest = SubscribeProductRequest.builder()
                    .productId(subscribeRequest.productId())
                    .couponId(subscribeRequest.couponId())
                    .memberId(subscribeRequest.memberId())
                    .orderId(subscribeRequest.orderId())
                    .build();


            subscribePackageProduct(convertedRequest);
        } catch (JsonProcessingException e) {
            throw new BaseCustomException(INVALID_SUBSCRIPTION_REQUEST);
        } catch(Exception e) {
            assert subscribeRequest != null;
            KafkaOrderRollbackRequest rollbackRequest = new KafkaOrderRollbackRequest(subscribeRequest.productId(), subscribeRequest.memberId(), subscribeRequest.couponId(), subscribeRequest.orderId());
            sendRollbackOrderMessage(rollbackRequest);
        }
    }

    public void subscribePackageProduct(SubscribeProductRequest subscribeRequest) {
        Member member = memberRepository.findByIdOrThrow(subscribeRequest.memberId());
        Subscription subscription = Subscription.builder()
                .packageId(subscribeRequest.productId())
                .member(member)
                .payDate(LocalDate.now().plusDays(28))
                .build();
        subscriptionRepository.save(subscription);
    }

    private void sendRollbackOrderMessage(KafkaOrderRollbackRequest rollbackMessage) {
        kafkaTemplate.send("order-rollback-topic", rollbackMessage);
    }

    public PaymentMethodGetResponse getPaymentMethod(Long memberId) {
        Member member = memberRepository.findByIdOrThrow(memberId);
        return PaymentMethodGetResponse.builder()
                .paymentMethodValue(member.getPaymentMethod().getPaymentMethodValue())
                .paymentMethodType(member.getPaymentMethod())
                .billingKey(member.getBillingKey())
                .logoImageUrl(member.getPaymentMethod().getLogoImageUrl())
                .build();
    }

    public Optional<SubscriptionGetResponse> getSubscriptionByProductId(Long productId, Long memberId) {
        Member member = memberRepository.findByIdOrThrow(memberId);
        Optional<Subscription> subscriptionOpt = subscriptionRepository.findByMemberAndPackageId(member, productId);

        if(subscriptionOpt.isPresent()) {
            return Optional.of(SubscriptionGetResponse.fromEntity(subscriptionOpt.get()));
        } else {
            return Optional.empty();
        }
    }
}
