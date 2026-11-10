package com.example.backend.domain.site.entity;

import jakarta.persistence.*;
import jdk.jfr.Name;

@Entity
@Table(name = "Zones")
public class Zone {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    private Site site;
}
