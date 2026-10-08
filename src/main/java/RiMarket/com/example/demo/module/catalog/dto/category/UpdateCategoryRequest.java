package RiMarket.com.example.demo.module.catalog.dto.category;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record UpdateCategoryRequest(
        @NotBlank(message = "Category name is required")
        String name,

        UUID parentId
) {
}
