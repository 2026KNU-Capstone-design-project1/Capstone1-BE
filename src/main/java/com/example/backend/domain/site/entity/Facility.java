package com.example.backend.domain.site.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "Facilities")
public class Facility {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;
}
