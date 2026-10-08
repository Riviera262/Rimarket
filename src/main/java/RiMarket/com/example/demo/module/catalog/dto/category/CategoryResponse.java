package RiMarket.com.example.demo.module.catalog.dto.category;

import java.util.List;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        UUID parentId,
        List<CategoryResponse> subCategoriesResponses) {
}
