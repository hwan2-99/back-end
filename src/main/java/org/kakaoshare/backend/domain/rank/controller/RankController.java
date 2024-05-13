package org.kakaoshare.backend.domain.rank.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kakaoshare.backend.domain.rank.dto.RankResponse;
import org.kakaoshare.backend.domain.rank.entity.RankType;
import org.kakaoshare.backend.domain.rank.entity.TargetType;
import org.kakaoshare.backend.domain.rank.service.RankService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/ranking")
public class RankController {
    private final RankService rankService;

    @GetMapping
    public ResponseEntity<?> getTopRankedProducts(@PageableDefault(size = 20) Pageable pageable) {
        Page<RankResponse> rankResponses = rankService.getTopRankedProducts(pageable);
        return ResponseEntity.ok(rankResponses);
    }

    @GetMapping("/realtime")
    public ResponseEntity<?> getRealTimeRankedProduct(
            @RequestParam(name = "rankType", required = false, defaultValue = "MANY_WISH") RankType rankType,
            @RequestParam(name = "targetType", required = false, defaultValue = "ALL") TargetType targetType,
            @RequestParam(name = "minPrice", required = false, defaultValue = "0") int minPrice,
            @RequestParam(name = "maxPrice", required = false, defaultValue = "9999") int maxPrice) {
        List<RankResponse> rankResponses = rankService.findProductsByFilters(rankType, targetType, minPrice, maxPrice);
        return ResponseEntity.ok(rankResponses);
    }
}
