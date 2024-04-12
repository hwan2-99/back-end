package org.kakaoshare.backend.domain.gift.repository.query;

import org.kakaoshare.backend.domain.gift.dto.GiftDescriptionResponse;
import org.kakaoshare.backend.domain.gift.dto.GiftDetailResponse;

public interface GiftRepositoryCustom {
    GiftDetailResponse findGiftDetail(Long giftId);
    GiftDescriptionResponse findGiftDescription(Long giftId);
}
