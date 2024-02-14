package org.kakaoshare.backend.domain.category.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.kakaoshare.backend.domain.brand.dto.TabType;
import org.kakaoshare.backend.domain.category.entity.Category;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class CategoryDto {
    private Long categoryId;
    private String categoryName;
    private List<CategoryDto> subCategories;
    private TabType defaultTab;
    
    public CategoryDto(Long categoryId, String categoryName, List<CategoryDto> subCategories) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.subCategories = subCategories;
        this.defaultTab = TabType.BRAND;//브랜드를 조회하는것이 화면 로딩과정에서 쿼리를 최소화 가능해보임
    }
    
    public static CategoryDto of(Category category) {
        return new CategoryDto(
                category.getCategoryId(),
                category.getName(),
                category.getChildren().stream()
                        .map(CategoryDto::of).toList());
    }
}
