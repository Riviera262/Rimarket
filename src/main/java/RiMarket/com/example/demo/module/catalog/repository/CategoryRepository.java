package RiMarket.com.example.demo.module.catalog.repository;

import RiMarket.com.example.demo.module.catalog.entity.Category;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @EntityGraph(attributePaths = {"subCategories", "subCategories.subCategories"})
    List<Category> findByParentIsNull();

    List<Category> findByParent_Id(UUID parentId);

    boolean existsByParent_Id(UUID parentId);

    boolean existsByParentIsNullAndNameIgnoreCase(String name);

    boolean existsByParent_IdAndNameIgnoreCase(UUID parentId, String name);

    boolean existsByParentIsNullAndNameIgnoreCaseAndIdNot(String name, UUID id);

    boolean existsByParent_IdAndNameIgnoreCaseAndIdNot(UUID parentId, String name, UUID id);
}
