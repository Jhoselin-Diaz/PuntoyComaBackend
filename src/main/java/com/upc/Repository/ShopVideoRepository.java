package com.upc.Repository;

import com.upc.Entity.ShopVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShopVideoRepository extends JpaRepository<ShopVideo, Long> {
}
