package vn.sportbooking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.sportbooking.entity.Payment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByTxnRef(String txnRef);
    List<Payment> findByBookingIdOrderByCreatedAtDesc(Long bookingId);
}
