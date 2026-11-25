package com.seatsense.waitlist;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatHoldRepository extends JpaRepository<SeatHold, Long> {

    List<SeatHold> findByStatus(String status);

    Optional<SeatHold> findByStudentIdAndStatus(Long studentId, String status);

    Optional<SeatHold> findBySeatIdAndStatus(Long seatId, String status);

    List<SeatHold> findByStatusAndExpiresAtBefore(String status, Instant now);
}
