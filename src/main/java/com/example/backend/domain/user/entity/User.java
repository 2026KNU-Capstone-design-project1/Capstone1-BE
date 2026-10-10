
package com.example.backend.domain.user.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", length = 255)
    private String loginId;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "hp")
    private Long hp;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "created_at", nullable = false,
            columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @Column(name = "role", length = 255)
    private String role;

    @Column(name = "site_id")
    private Long siteId;

    protected User() {
    }

    public User(
            String loginId,
            String password,
            String name,
            Long hp,
            String email
    ) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.hp = hp;
        this.email = email;
        this.role = "USER";
        this.siteId = null;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getPassword() {
        return password;
    }

    public String getName() {
        return name;
    }

    public Long getHp() {
        return hp;
    }

    public String getEmail() {
        return email;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getRole() {
        return role;
    }

    public Long getSiteId() {
        return siteId;
    }
}
