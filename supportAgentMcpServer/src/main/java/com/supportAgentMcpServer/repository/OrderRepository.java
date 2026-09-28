package com.supportAgentMcpServer.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.supportAgentMcpServer.entities.CustomerOrder;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

    Optional<CustomerOrder> findByOrderNumber(String orderNumber);

    List<CustomerOrder> findByCustomerEmailIgnoreCaseOrderByOrderDateDesc(String email);
}