package RiMarket.com.example.demo.module.catalog.domain.policy;

import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainException;
import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainViolation;
import RiMarket.com.example.demo.module.catalog.entity.Category;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CategoryHierarchyPolicyTest {

    private final CategoryHierarchyPolicy policy = new CategoryHierarchyPolicy();

    @Test
    void allowsThreeLevelsButRejectsAFourthLevel() {
        Category root = new Category("Root", null);
        Category levelTwo = new Category("Level two", root);
        Category levelThree = new Category("Level three", levelTwo);

        assertDoesNotThrow(() -> policy.validateCreateUnder(levelTwo));

        CatalogDomainException exception = assertThrows(
                CatalogDomainException.class,
                () -> policy.validateCreateUnder(levelThree)
        );

        assertEquals(CatalogDomainViolation.CATEGORY_DEPTH_EXCEEDED, exception.getViolation());
    }

    @Test
    void rejectsMovingCategoryBelowItsOwnDescendant() {
        Category root = new Category("Root", null);
        Category child = new Category("Child", root);

        CatalogDomainException exception = assertThrows(
                CatalogDomainException.class,
                () -> policy.validateMove(root, child)
        );

        assertEquals(CatalogDomainViolation.CATEGORY_CIRCULAR_DEPENDENCY, exception.getViolation());
    }

    @Test
    void includesMovedSubtreeHeightWhenCheckingMaximumDepth() {
        Category subtreeRoot = new Category("Subtree root", null);
        new Category("Subtree child", subtreeRoot);

        Category targetRoot = new Category("Target root", null);
        Category targetLevelTwo = new Category("Target level two", targetRoot);

        CatalogDomainException exception = assertThrows(
                CatalogDomainException.class,
                () -> policy.validateMove(subtreeRoot, targetLevelTwo)
        );

        assertEquals(CatalogDomainViolation.CATEGORY_DEPTH_EXCEEDED, exception.getViolation());
    }
}
