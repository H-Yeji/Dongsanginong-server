package org.samtuap.inong.domain.subscription.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.samtuap.inong.domain.member.dto.MemberSubsCancelRequest;
import org.samtuap.inong.domain.member.dto.MemberSubscriptionListResponse;
import org.samtuap.inong.domain.member.dto.MemberSubscriptionResponse;
import org.samtuap.inong.domain.member.dto.PaymentMethodGetResponse;
import org.samtuap.inong.domain.subscription.dto.BillingKeyRegisterRequest;
import org.samtuap.inong.domain.subscription.dto.SubscriptionListGetResponse;
import org.samtuap.inong.domain.subscription.service.SubscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/subscription")
@RequiredArgsConstructor
@RestController
public class SubscriptionController {
    private final SubscriptionService subscriptionService;
    @PostMapping("/payment/method")
    public ResponseEntity<Void> registerPaymentMethod(@RequestHeader("myId") Long memberId,
                                                   @RequestBody @Valid BillingKeyRegisterRequest dto) {
        subscriptionService.registerPaymentMethod(memberId, dto);

        return ResponseEntity.ok(null);
    }

    @GetMapping("/payment/method")
    public ResponseEntity<PaymentMethodGetResponse> getPaymentMethod(@RequestHeader("myId") Long memberId) {
        PaymentMethodGetResponse paymentMethod = subscriptionService.getPaymentMethod(memberId);
        return ResponseEntity.ok(paymentMethod);
    }

    // feign 요청 용 API
    @GetMapping("/payment")
    public SubscriptionListGetResponse getSubscriptionToPay() {
        return subscriptionService.getSubscriptionToPay();

    }

    @GetMapping
    public ResponseEntity<MemberSubscriptionResponse> getSubscription(@RequestParam("id") Long memberId){
        MemberSubscriptionResponse memberSubscriptionResponse = subscriptionService.getSubscription(memberId);
        return new ResponseEntity<>(memberSubscriptionResponse, HttpStatus.OK);
    }

    @GetMapping("/list")
    public ResponseEntity<List<MemberSubscriptionListResponse>> getSubscriptionList(@RequestHeader("myId") Long memberId){
        List<MemberSubscriptionListResponse> subscriptionList = subscriptionService.getSubscriptionList(memberId);
        return new ResponseEntity<>(subscriptionList, HttpStatus.OK);
    }

    @PostMapping("/cancel")
    public ResponseEntity<MemberSubsCancelRequest> cancelSubscription(@RequestHeader("myId") Long memberId, @RequestParam("id") Long subsId){
        MemberSubsCancelRequest cancelSubscription = subscriptionService.cancelSubscription(memberId, subsId);
        return new ResponseEntity<>(cancelSubscription, HttpStatus.OK);
    }

}
