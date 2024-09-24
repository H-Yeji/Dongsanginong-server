package org.samtuap.inong.domain.delivery.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record FarmDetailGetResponse(@NotNull Long id,
                                    @NotNull String farmName,
                                    @NotNull String bannerImageUrl,
                                    @NotNull String profileImageUrl,
                                    @NotNull String farmIntro,
                                    @NotNull Long favoriteCount) {
}
