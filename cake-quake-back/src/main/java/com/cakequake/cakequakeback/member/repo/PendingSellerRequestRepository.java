package com.cakequake.cakequakeback.member.repo;

import com.cakequake.cakequakeback.member.entities.PendingSellerRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PendingSellerRequestRepository extends JpaRepository<PendingSellerRequest, Long>, PendingSellerRequestCustomRepository {

    Optional<Object> findByUserId(String userId);

    boolean existsByPhoneNumber(String phoneNumber);

    Optional<PendingSellerRequest> findByPhoneNumber(String phoneNumber);

    boolean existsByUserId(String userId);

}
