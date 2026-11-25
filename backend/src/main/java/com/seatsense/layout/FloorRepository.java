package com.seatsense.layout;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FloorRepository extends JpaRepository<Floor, String> {

    List<Floor> findAllByOrderByNumberAsc();
}
