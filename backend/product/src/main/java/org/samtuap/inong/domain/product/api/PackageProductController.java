package org.samtuap.inong.domain.product.api;

import lombok.RequiredArgsConstructor;
import org.samtuap.inong.common.exception.BaseCustomException;
import org.samtuap.inong.domain.product.dto.*;
import org.samtuap.inong.domain.product.service.PackageProductService;
import org.samtuap.inong.domain.product.dto.TopPackageGetResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.samtuap.inong.common.exceptionType.ProductExceptionType.PRODUCT_NOT_FOUND;


@RequiredArgsConstructor
@RequestMapping("/product")
@RestController
public class PackageProductController {
    private final PackageProductService packageProductService;
    @GetMapping
    public String testApi() {
        return "product-test!!";
    }

    @GetMapping("/test")
    public void exceptionTest() {
        throw new BaseCustomException(PRODUCT_NOT_FOUND);
    }

    @GetMapping("/no-auth/top10")
    public List<TopPackageGetResponse> getTopPackages() {
        return packageProductService.getTopPackages();
    }

    //  Feign 요청용 메서드
    @GetMapping("/info/{id}")
    public PackageProductResponse getPackageProduct(@PathVariable("id") Long packageProductId) {
        return packageProductService.getProductInfo(packageProductId);
    }

    //  Feign 요청용 메서드(삭제 처리된 상품까
    @GetMapping("/info/contain-deleted/{id}")
    public PackageProductResponse getPackageProductContainDeleted(@PathVariable("id") Long packageProductId) {
        return packageProductService.getProductInfoContainDeleted(packageProductId);
    }

    @PostMapping("/create")
    public ResponseEntity<PackageProductCreateResponse> createProduct(@RequestHeader Long sellerId,
                                                                      @RequestBody PackageProductCreateRequest request) {
        PackageProductCreateResponse packageProductCreateResponse = packageProductService.createPackageProduct(sellerId, request);
        return ResponseEntity.ok(packageProductCreateResponse);
    }

    //    Detail 메서드 (Feign이랑 url이 다름)
    @GetMapping("/no-auth/detail/{id}")
    public PackageProductResponse getPackageProductDetail(@PathVariable("id") Long packageProductId) {
        return packageProductService.getProductInfo(packageProductId);
    }

    @GetMapping("/no-auth/no-cache/detail/{id}")
    public PackageProductResponse getProductInfoNoCache(@PathVariable("id") Long packageProductId) {
        return packageProductService.getProductInfoNoCache(packageProductId);
    }

    // Feign 요청용 메서드
    @PostMapping("/info")
    List<PackageProductResponse> getPackageProductList(@RequestBody List<Long> ids) {
        return packageProductService.getProductInfoList(ids);
    }

    //  Feign 요청용 메서드
    @PostMapping("/subscription/list")
    public List<PackageProductSubsResponse> getProductSubsList(@RequestBody List<Long> subscriptionIds){
        return packageProductService.getProductSubsList(subscriptionIds);
    }

    // Feign 요청용 메서드
    @PostMapping("/info/contain-deleted")
    List<PackageProductResponse> getPackageProductListContainDeleted(@RequestBody List<Long> ids) {
        return packageProductService.getProductInfoListContainDeleted(ids);
    }


    // Feign 요청용 메서드
    @PostMapping("/info/contain-deleted/name-only")
    List<PackageStatisticResponse> getPackageProductListContainDeletedNameOnly(@RequestBody List<Long> ids) {
        return packageProductService.getProductInfoListContainDeletedNameOnly(ids);
    }

    @GetMapping("/no-auth/for-sale/{id}")
    public Page<PackageProductForSaleListResponse> getForSalePackageProduct(@PathVariable("id") Long farmId,
                                                                             @PageableDefault(size = 12, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return packageProductService.getForSalePackageProduct(farmId, pageable);
    }

    @GetMapping("/no-auth")
    public ResponseEntity<Page<AllPackageListResponse>> getAllPackageList(@PageableDefault(size = 12, sort = "id", direction = Sort.Direction.DESC) Pageable pageable){
        return ResponseEntity.ok(packageProductService.getAllPackageList(pageable));
    }

    @GetMapping("/no-auth/search")
    public ResponseEntity<Page<AllPackageListResponse>> searchProduct(@PageableDefault(size = 12, sort = "id", direction = Sort.Direction.DESC) Pageable pageable, @RequestParam("packageName") String packageName){
        return ResponseEntity.ok(packageProductService.searchProduct(pageable, packageName));
    }

    /**
     * member에서 위시리스트 관련 feign 요청
     */
    @PostMapping("/{packageProductId}/increase-wish")
    void increaseWish(@PathVariable("packageProductId") Long packageProductId) {
        packageProductService.increaseWish(packageProductId);
    }

    @PostMapping("/{packageProductId}/decrease-wish")
    void decreaseWish(@PathVariable("packageProductId") Long packageProductId) {
        packageProductService.decreaseWish(packageProductId);
    }

    /**
     * 할인건이 있는 상품 목록 출력
     */
    @GetMapping("/discount/list")
    public ResponseEntity<Page<PackageProductDiscountResponse>> discountProductList(
            @RequestHeader("sellerId") Long sellerId,
            @PageableDefault(size = 15, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return new ResponseEntity<>(packageProductService.discountProductList(sellerId, pageable), HttpStatus.OK);
    }

    @GetMapping("/healthcheck")
    public String healthcheck() {
        return "ok!";
    }
}
