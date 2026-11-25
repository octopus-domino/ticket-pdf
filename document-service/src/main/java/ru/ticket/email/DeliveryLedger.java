package ru.ticket.email;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryLedger {
  private final DeliveryReceiptRepository repository;

  public DeliveryLedger(DeliveryReceiptRepository repository) {
    this.repository = repository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public synchronized String reserve(String id) {
    var existing = repository.findById(id);
    if (existing.isPresent()) return existing.get().getStatus();
    repository.saveAndFlush(new DeliveryReceipt(id));
    return "RESERVED";
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void finish(String id, boolean sent) {
    var receipt = repository.findById(id).orElseThrow();
    if (sent) receipt.sent();
    else receipt.uncertain();
    repository.save(receipt);
  }
}
