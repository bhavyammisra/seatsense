package com.seatsense.waitlist;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {

    /** The student's live entry (WAITING, OFFERED or ACCEPTED). At most one exists (V6 index). */
    @Query("""
        select e from WaitlistEntry e where e.studentId = :studentId
        and e.status in (com.seatsense.waitlist.WaitlistStatus.WAITING,
                         com.seatsense.waitlist.WaitlistStatus.OFFERED,
                         com.seatsense.waitlist.WaitlistStatus.ACCEPTED)""")
    Optional<WaitlistEntry> findLive(@Param("studentId") Long studentId);

    /** Who is next for a seat on this floor: the oldest WAITING entry that wants this floor or any floor. */
    @Query("""
        select e from WaitlistEntry e
        where e.status = com.seatsense.waitlist.WaitlistStatus.WAITING
          and (e.floorPref is null or e.floorPref = :floorId)
        order by e.joinedAt asc, e.id asc""")
    List<WaitlistEntry> queueFor(@Param("floorId") String floorId);

    /**
     * Place in the queue: how many WAITING entries joined earlier and want a seat this student could
     * also take. For a floor preference that is the same floor or any floor; for "any floor", everyone.
     */
    @Query("""
        select count(e) from WaitlistEntry e
        where e.status = com.seatsense.waitlist.WaitlistStatus.WAITING and e.joinedAt < :joinedAt
          and (:floorPref is null or e.floorPref is null or e.floorPref = :floorPref)""")
    long countAheadOf(@Param("joinedAt") Instant joinedAt, @Param("floorPref") String floorPref);
}
