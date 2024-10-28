package org.samtuap.inong.common.client;

import org.samtuap.inong.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Profile;

@Profile("local")
@FeignClient(name = "farm-service", configuration = FeignConfig.class)
public interface FarmFeignLocal extends FarmFeign {
}
