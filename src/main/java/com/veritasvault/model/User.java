package com.veritasvault.model;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.veritasvault.model.enums.Role;
import com.veritasvault.model.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// these two tells take this class and map it to a table named users in postSQL

@Entity
@Table(name="users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// this is used to exclude the password hash becuase we dont want it to show becuase its apassword

@ToString(exclude ="passwordHash")

public class User {

    // this is to delcare our PK in the table
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

/// this is used to make sure it is not null it will give an error that it shouldnt be null value

    @Column(name = "is_email_verified", nullable = false)
    /// when an object is created it will force the data of verified email to be false if no entry is inputed
    /// we cant have an null it doesnt make sense
    @Builder.Default
    private Boolean isEmailVerified = false;

    // postSQL doesnt know what n enum is it only kows the type like number an integer or string
    ///by defualt JPA stores the enums as numbers
    /// for example roleAdmin will be 0 and then 1 and so on it will work no problem but what will happen when you add another user lets say for exmaple new role what will this cause
    /// the number to break that new number will be an old number and this will break our system and cause confusion
    /// this till to store string text role admin or the other the same way
    @Enumerated(EnumType.STRING)
    @Column(name="role" , nullable = false)
    private Role role;

    /// the reason we didnt make it it builder in the roles becuase the roles cant go to a defualt we need to know what exaclty the user is without it nothign will work
    /// and making a defualt will casue poeple who have no access and reason to read the default whatever it is we choose to view it
    @Column(name="full_name",nullable = false)
    private String fullName;

    /// this is so that when convnerting User object to JSON response


    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "password_hash",nullable = false)
    private String passwordHash;


    // same logic as the enums for the status
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "profile_picture_url")
    private String profilePictureUrl;


    @Column(name = "verification_token")
    private String verificationToken;

    @Column(name = "verification_token_expiry")
    private LocalDateTime verificationTokenExpiry;

    @Column(name = "password_reset_token")
    private String passwordResetToken;

    @Column(name = "password_reset_token_expiry")
    private LocalDateTime passwordResetTokenExpiry;


    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @org.hibernate.annotations.CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @org.hibernate.annotations.UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

}
