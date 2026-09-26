package de.conduit.articles;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArticleRepository extends JpaRepository<Article, UUID> {
    @Query("select a from Article a where a.slug = :slug")
    Optional<Article> findBySlug(@Param("slug") String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Article a where a.slug = :slug")
    Optional<Article> findBySlugForUpdate(@Param("slug") String slug);

    @Query("select distinct t from Article a join a.tags t order by t")
    List<String> findAllTags();
}