package ru.ticket.email;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryReceiptRepository extends JpaRepository<DeliveryReceipt, String> {}
