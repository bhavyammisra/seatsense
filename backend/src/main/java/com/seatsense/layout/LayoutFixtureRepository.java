package com.seatsense.layout;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LayoutFixtureRepository extends JpaRepository<LayoutFixture, Long> {

    List<LayoutFixture> findAllByOrderByIdAsc();
}
