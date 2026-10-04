package com.veritasvault.repository;

import com.veritasvault.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
public interface UserRepository extends JpaRepository<User,Long> {



    boolean existsByEmail(String email);


    Optional<User> findByEmail(String email);


    Optional<User> findByVerificationToken(String verificationToken);


    Optional<User> findByPasswordResetToken(String passwordResetToken);



}
