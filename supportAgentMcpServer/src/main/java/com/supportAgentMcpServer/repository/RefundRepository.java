package com.supportAgentMcpServer.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.supportAgentMcpServer.entities.Refund;

public interface RefundRepository extends JpaRepository<Refund, Long> {
}