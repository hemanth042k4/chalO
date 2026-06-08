package com.chalo.repository;

import com.chalo.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findAllByOrderByNameAsc();

    Optional<Tag> findBySlug(String slug);

    // Bulk-load tags selected by the user on the search/profile form
    List<Tag> findByIdIn(Collection<Long> ids);

    // Resolve multiple slugs at once — used by ExploreController to convert
    // ?tags= slug params from the dashboard search form into Tag entities.
    List<Tag> findBySlugIn(Collection<String> slugs);
}
