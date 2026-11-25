package com.seatsense.layout;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByFloorIdOrderByGyAscGxAsc(String floorId);

    Optional<Seat> findByCode(String code);
}
