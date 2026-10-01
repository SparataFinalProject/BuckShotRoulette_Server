package com.buckshot.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String guestKeyHash;

    @Column(unique = true, length = 12)
    private String nickname;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false)
    private int wins;

    @Column(nullable = false)
    private int losses;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public User(String guestKeyHash, int rating) {
        this.guestKeyHash = guestKeyHash;
        this.rating = rating;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }
}
