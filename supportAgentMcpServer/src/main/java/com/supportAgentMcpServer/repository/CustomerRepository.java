package com.supportAgentMcpServer.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.supportAgentMcpServer.entities.Customer;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByEmailIgnoreCase(String email);
}
