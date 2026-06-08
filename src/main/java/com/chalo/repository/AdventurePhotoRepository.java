package com.chalo.repository;

import com.chalo.model.Adventure;
import com.chalo.model.AdventurePhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdventurePhotoRepository extends JpaRepository<AdventurePhoto, Long> {

    List<AdventurePhoto> findByAdventureOrderByDisplayOrderAsc(Adventure adventure);

    // Shift display_order values when re-ordering — caller updates each photo's
    // displayOrder field then calls saveAll().
    long countByAdventure(Adventure adventure);
}
