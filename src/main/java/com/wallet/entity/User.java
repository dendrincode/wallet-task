package com.wallet.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @Column("user_id")
    private String userId;

    @Column("created_at")
    private LocalDateTime createdAt;

    public User(String userId) {
        this.userId = userId;
        this.createdAt = LocalDateTime.now();
    }
}
