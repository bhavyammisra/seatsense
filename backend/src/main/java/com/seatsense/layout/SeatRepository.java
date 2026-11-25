package com.seatsense.layout;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByFloorIdOrderByGyAscGxAsc(String floorId);

    Optional<Seat> findByCode(String code);

    /**
     * The seat row, locked until the transaction ends. Taking a seat, holding it for the
     * waitlist and taking it out of service all load the seat this way, so they run one at a time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.code = :code")
    Optional<Seat> findByCodeForUpdate(@Param("code") String code);
}
