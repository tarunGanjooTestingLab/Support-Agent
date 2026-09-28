package com.supportAgentMcpServer.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.supportAgentMcpServer.entities.Payment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderOrderNumberOrderByChargedAtAsc(String orderNumber);

    Optional<Payment> findByTransactionRef(String transactionRef);
}