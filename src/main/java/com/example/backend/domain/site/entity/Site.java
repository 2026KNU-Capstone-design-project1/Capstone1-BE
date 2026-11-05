package com.example.backend.domain.site.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "Sites")
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", unique = true)
    private String name;

    @Column(name = "address")
    private String address;

    @Column(name = "user_id")
    private Long userId;

    public Long getId() {
        return id;
    }
}
