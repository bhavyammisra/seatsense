package com.seatsense.layout;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LayoutZoneRepository extends JpaRepository<LayoutZone, String> {

    List<LayoutZone> findAllByOrderByFloorIdAscSortOrderAsc();
}
