package com.example.backend.domain.site.repository;

import com.example.backend.domain.site.entity.Site;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SiteRepository extends JpaRepository<Site, Long> {

    Optional<Site> findByName(String name);
}
