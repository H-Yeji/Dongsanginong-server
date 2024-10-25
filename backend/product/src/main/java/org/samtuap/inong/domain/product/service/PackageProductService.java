package org.samtuap.inong.domain.product.service;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.samtuap.inong.common.client.OrderFeign;
import org.samtuap.inong.common.exception.BaseCustomException;
import org.samtuap.inong.domain.discount.entity.Discount;
import org.samtuap.inong.domain.discount.repository.DiscountRepository;
import org.samtuap.inong.domain.farm.entity.Farm;
import org.samtuap.inong.domain.farm.repository.FarmRepository;
import org.samtuap.inong.domain.product.dto.*;
import org.samtuap.inong.domain.product.entity.PackageProduct;
import org.samtuap.inong.domain.product.entity.PackageProductImage;
import org.samtuap.inong.domain.product.repository.PackageProductImageRepository;
import org.samtuap.inong.domain.product.repository.PackageProductRepository;
import org.samtuap.inong.domain.seller.entity.Seller;
import org.samtuap.inong.domain.seller.repository.SellerRepository;
import org.samtuap.inong.search.service.PackageProductSearchService;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

import static org.samtuap.inong.common.exceptionType.ProductExceptionType.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class PackageProductService {
    private final PackageProductRepository packageProductRepository;
    private final PackageProductImageRepository packageProductImageRepository;
    private final OrderFeign orderFeign;
    private final FarmRepository farmRepository;
    private final PackageProductImageService packageProductImageService;
    private final PackageProductSearchService packageProductSearchService;
    private final DiscountRepository discountRepository;
    private final CacheManager cacheManager;
    private final SellerRepository sellerRepository;


    public List<TopPackageGetResponse> getTopPackages() {
        List<Long> topPackages = orderFeign.getTopPackages();
        List<PackageProduct> products = packageProductRepository.findAllByIds(topPackages);

        return products.stream()
                .map(product -> {
                    PackageProductImage firstByPackageProduct = packageProductImageRepository.findFirstByPackageProduct(product);
                    if(firstByPackageProduct == null) {
                        throw new BaseCustomException(PRODUCT_IMAGE_NOT_FOUND);
                    }
                    // discount 정보 가져오기
                    if (product.getDiscountId() != null) {
                        Discount discount = discountRepository.findByIdThrow(product.getDiscountId());
                        Long counts = orderFeign.getAllOrders(product.getId());
                        return TopPackageGetResponse.fromEntity(product, firstByPackageProduct.getImageUrl(), counts, discount.getDiscount(), discount.isDiscountActive());
                    } else {
                        Long counts = orderFeign.getAllOrders(product.getId());
                        return TopPackageGetResponse.fromEntity(product, firstByPackageProduct.getImageUrl(), counts, null, false);
                    }
                })
                .sorted(Comparator.comparingInt(product -> topPackages.indexOf(product.id())))
                .collect(Collectors.toList());
    }

    public Page<AllPackageListResponse> getAllPackageList(Pageable pageable) {
        Page<PackageProduct> products = packageProductRepository.findAll(pageable);
        Page<AllPackageListResponse> productList = products.map(packageProduct -> {
            PackageProductImage packageProductImage = packageProductImageRepository.findFirstByPackageProduct(packageProduct);
            String imageUrl = packageProductImage.getImageUrl();
            Long orderCount = orderFeign.getAllOrders(packageProduct.getId());
            if (packageProduct.getDiscountId() != null) {
                Discount discount = discountRepository.findByIdThrow(packageProduct.getDiscountId());
                return AllPackageListResponse.fromEntity(packageProduct, imageUrl, orderCount, discount.getDiscount(), discount.isDiscountActive());
            } else {
                return AllPackageListResponse.fromEntity(packageProduct, imageUrl, orderCount, null, false);
            }
        });
        return productList;
    }

    @Cacheable(value = "PackageDetail", key = "#packageProductId", cacheManager = "contentCacheManager")
    public PackageProductResponse getProductInfo(Long packageProductId) {
        PackageProduct packageProduct = packageProductRepository.findByIdOrThrow(packageProductId);
        List<PackageProductImage> packageProductImage = packageProductImageRepository.findAllByPackageProduct(packageProduct);

        if (packageProduct.getDiscountId() != null) {
            Discount discount = discountRepository.findByIdThrow(packageProduct.getDiscountId());
            return PackageProductResponse.fromEntity(packageProduct, packageProductImage, discount.getDiscount(), discount.isDiscountActive());
        } else {
            return PackageProductResponse.fromEntity(packageProduct, packageProductImage, null, false);
        }
    }

    public PackageProductResponse getProductInfoNoCache(Long packageProductId) {
        PackageProduct packageProduct = packageProductRepository.findByIdOrThrow(packageProductId);
        List<PackageProductImage> packageProductImage = packageProductImageRepository.findAllByPackageProduct(packageProduct);
        if (packageProduct.getDiscountId() != null) {
            Discount discount = discountRepository.findByIdThrow(packageProduct.getDiscountId());
            return PackageProductResponse.fromEntity(packageProduct, packageProductImage, discount.getDiscount(), discount.isDiscountActive());
        } else {
            return PackageProductResponse.fromEntity(packageProduct, packageProductImage, null, false);
        }
    }

    @Transactional
    public PackageProductCreateResponse createPackageProduct(Long sellerId, PackageProductCreateRequest request) {
        // 농장 조회 후 사용
        Farm farm = farmRepository.findBySellerId(sellerId)
                .orElseThrow(() -> new BaseCustomException(FARM_NOT_FOUND));

        // 상품 엔티티 생성 및 저장
        PackageProduct packageProduct = PackageProductCreateRequest.toEntity(farm, request);
        PackageProduct savedPackageProduct = packageProductRepository.save(packageProduct);

        List<String> imageUrls = request.imageUrls();

        // 이미지 저장 로직 호출
        packageProductImageService.saveImages(savedPackageProduct, imageUrls);

        // elasticsearch✔️ : open search에 인덱싱
//        PackageProductDocument packageProductDocument = PackageProductDocument.convertToDocument(savedPackageProduct);
//        packageProductSearchService.indexProductDocument(packageProductDocument);

        // 저장된 엔티티를 DTO로 반환
        return PackageProductCreateResponse.fromEntity(savedPackageProduct, imageUrls);
    }

    @Transactional
    public Page<SellerPackageListGetResponse> getSellerPackages(Long sellerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PackageProduct> packageProductPage = packageProductRepository.findBySellerId(sellerId, pageable);
        return SellerPackageListGetResponse.fromEntities(packageProductPage);
    }

    @Transactional
    public void deletePackage(Long sellerId, Long packageId) {
        PackageProduct packageProduct = packageProductRepository.findByIdOrThrow(packageId);
        if (!packageProduct.getFarm().getSellerId().equals(sellerId)) {
            throw new BaseCustomException(UNAUTHORIZED_ACTION);
        }
        packageProductRepository.delete(packageProduct);

        Cache productCache = cacheManager.getCache("PackageDetail");
        Object cachedProduct = Optional.ofNullable(productCache)
                .map(cache -> cache.get(packageId))
                .map(Cache.ValueWrapper::get)
                .orElse(null);
        if (cachedProduct != null) {
            productCache.evict(packageId);
        }

        // elasticsearch✔️ : 삭제
//        packageProductSearchService.deleteProduct(String.valueOf(packageId));
    }

    @Transactional
    public void updatePackageProduct(Long sellerId, Long packageId, PackageProductUpdateRequest request) {
        // 상품 조회 및 검증
        PackageProduct packageProduct = packageProductRepository.findById(packageId)
                .orElseThrow(() -> new BaseCustomException(PRODUCT_NOT_FOUND));

        if (!packageProduct.getFarm().getSellerId().equals(sellerId)) {
            throw new BaseCustomException(UNAUTHORIZED_ACTION);
        }

        // 상품 정보 및 이미지 수정
        request.updatePackageProduct(packageProduct, packageProductImageService);

        // 수정된 상품 정보 저장
        packageProductRepository.save(packageProduct);

        Cache productCache = cacheManager.getCache("PackageDetail");
        Object cachedProduct = Optional.ofNullable(productCache)
                .map(cache -> cache.get(packageId))
                .map(Cache.ValueWrapper::get)
                .orElse(null);
        if (cachedProduct != null) {
            productCache.evict(packageId);
        }

        // elasticsearch✔️ : open search에 수정
//        PackageProductDocument updateProduct = PackageProductDocument.convertToDocument(packageProduct);
//        packageProductSearchService.updateProduct(updateProduct);
    }

    // Feign 요청 용
    public List<PackageProductResponse> getProductInfoList(List<Long> ids) {
        List<PackageProduct> packageProducts = packageProductRepository.findAllById(ids);
        return packageProducts.stream().map(p -> {
                    if (p.getDiscountId() != null) {
                        Discount discount = discountRepository.findByIdThrow(p.getDiscountId());
                        return PackageProductResponse.fromEntity(p, new ArrayList<>(), discount.getDiscount(), discount.isDiscountActive());
                    } else {
                        return PackageProductResponse.fromEntity(p, new ArrayList<>(), null, false);
                    }
                }).toList();
    }

    public List<PackageProductResponse> getProductInfoListContainDeleted(List<Long> ids) {
        List<PackageProduct> packageProducts = packageProductRepository.findAllByIdContainDeleted(ids);
        return packageProducts.stream().map(p -> {
                    if (p.getDiscountId() != null) {
                        Discount discount = discountRepository.findByIdThrow(p.getDiscountId());
                        return PackageProductResponse.fromEntity(p, new ArrayList<>(), discount.getDiscount(), discount.isDiscountActive());
                    } else {
                        return PackageProductResponse.fromEntity(p, new ArrayList<>(), null, false);
                    }
                }).toList();
    }

    public List<PackageStatisticResponse> getProductInfoListContainDeletedNameOnly(List<Long> ids) {
        return packageProductRepository.findAllByIdContainDeletedNameOnly(ids);
    }
  
    @Transactional
    public List<PackageProductSubsResponse> getProductSubsList(List<Long> subscriptionIds) {
        List<PackageProduct> subsPackageProductList = packageProductRepository.findAllByIds(subscriptionIds);
        return subsPackageProductList.stream()
                .map(packageProduct -> {
                    String imageUrl = packageProductImageRepository.findFirstByPackageProduct(packageProduct).getImageUrl();
                    Farm farm = farmRepository.findByIdOrThrow(packageProduct.getFarm().getId());
                    return PackageProductSubsResponse.fromEntity(packageProduct, imageUrl, farm);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public Page<PackageProductForSaleListResponse> getForSalePackageProduct(Long farmId, Pageable pageable) {
        Page<PackageProduct> packageProducts = packageProductRepository.findAllByFarmId(farmId, pageable);

        return packageProducts.map(p -> {
            PackageProductImage image = packageProductImageRepository.findFirstByPackageProduct(p);
            String imageUrl = (image != null) ? image.getImageUrl() : "default-image-url";
            Farm farm = farmRepository.findByIdOrThrow(p.getFarm().getId());
            return PackageProductForSaleListResponse.fromEntity(p, imageUrl, farm);
        });
    }
    // cache 처리 전 메서드 (테스트용)
    @Transactional
    public List<PackageProductForSaleListResponse> getForSalePackageProductNoCache(Long farmId) {
        List<PackageProduct> packageProducts = packageProductRepository.findAllByFarmId(farmId);
        return packageProducts.stream()
                .map(p -> {
                    String imageUrl = packageProductImageRepository.findFirstByPackageProduct(p).getImageUrl();
                    Farm farm = farmRepository.findByIdOrThrow(p.getFarm().getId());
                    return PackageProductForSaleListResponse.fromEntity(p, imageUrl, farm);
                })
                .collect(Collectors.toList());
    }

    public Page<AllPackageListResponse> searchProduct(Pageable pageable, String packageName) {
        Specification<PackageProduct> specification = new Specification<PackageProduct>() {
            @Override
            public Predicate toPredicate(Root<PackageProduct> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
                List<Predicate> predicates = new ArrayList<>();

                if (!packageName.isEmpty()) {
                    predicates.add(criteriaBuilder.like(root.get("packageName"), "%" + packageName + "%"));
                }

                Predicate[] predicateArr = new Predicate[predicates.size()];
                for (int i = 0; i < predicateArr.length; i++) {
                    predicateArr[i] = predicates.get(i);
                }

                Predicate predicate = criteriaBuilder.and(predicateArr);
                return predicate;
            }
        };
        Page<PackageProduct> products = packageProductRepository.findAll(specification, pageable);
        Page<AllPackageListResponse> productList = products.map(packageProduct -> {
            PackageProductImage packageProductImage = packageProductImageRepository.findFirstByPackageProduct(packageProduct);
            String imageUrl = packageProductImage.getImageUrl();
            Long orderCount = orderFeign.getAllOrders(packageProduct.getId());

            if (packageProduct.getDiscountId() != null) {
                Discount discount = discountRepository.findByIdThrow(packageProduct.getDiscountId());
                return AllPackageListResponse.fromEntity(packageProduct, imageUrl, orderCount, discount.getDiscount(), discount.isDiscountActive());
            } else {
                return AllPackageListResponse.fromEntity(packageProduct, imageUrl, orderCount, null, false);
            }
        });
        return productList;
    }

    /**
     * member에서 wishList관련 feign
     */
    @Transactional
    public void increaseWish(Long packageProductId) {
        PackageProduct packageProduct = packageProductRepository.findByIdOrThrow(packageProductId);
        packageProduct.updateWishCount(packageProduct.getWishCount() + 1);
    }

    @Transactional
    public void decreaseWish(Long packageProductId) {
        PackageProduct packageProduct = packageProductRepository.findByIdOrThrow(packageProductId);
        packageProduct.updateWishCount(packageProduct.getWishCount() - 1);
    }

    /**
     * 할인건이 있는 상품 목록 출력
     */
    public Page<PackageProductDiscountResponse> discountProductList(Long sellerId, Pageable pageable) {
        // seller 가져오기
        Seller seller = sellerRepository.findByIdOrThrow(sellerId);
        // 해당 seller의 farm 가져오기
        Farm farm = farmRepository.findBySellerIdOrThrow(seller.getId());
        // 해당 farm의 모든 packgelist 가져오기
        Page<PackageProduct> discountPackageList = packageProductRepository.findAllByFarmIdAndDiscountIdIsNotNull(farm.getId(), pageable);

        return discountPackageList.map(discountPackage -> {
            Discount discount = discountRepository.findByIdThrow(discountPackage.getDiscountId());
            return PackageProductDiscountResponse.from(discountPackage, discount);
        });
    }
}
