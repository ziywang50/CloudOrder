package com.highvia.customerservice.Repository;

import com.highvia.customerservice.Entity.CustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<CustomerEntity, Long> {
    Optional<CustomerEntity> findByEmail(String email);

    @Modifying
    @Transactional  //
    @Query("UPDATE CustomerEntity c SET c.username = :userName WHERE c.email = :email")
    void updateNameByEmail(
            @Param("email") String email,
            @Param("userName") String userName
    );

    boolean existsByEmail(String email);
}
