package com.supportAgentMcpServer.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.supportAgentMcpServer.entities.SupportTicket;

import java.util.List;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    List<SupportTicket> findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(String email);
}