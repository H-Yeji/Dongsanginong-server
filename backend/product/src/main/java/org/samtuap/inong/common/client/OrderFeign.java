package org.samtuap.inong.common.client;

import org.samtuap.inong.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;


import java.time.LocalDate;
import java.util.List;

@FeignClient(name = "order-service", configuration = FeignConfig.class)
public interface OrderFeign {

    @GetMapping(value = "/order/package/top")
    List<Long> getTopPackages();


    @GetMapping("/order/package/{packageId}/count")
    Long getAllOrders(@PathVariable Long packageId);

    @GetMapping("/delivery/farm/{farmId}/count")
    Long getDeliveryCountByFarmId(@PathVariable Long farmId, @RequestParam("date") String date);
}
