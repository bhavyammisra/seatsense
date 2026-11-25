package com.seatsense.session;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeatSessionRepository extends JpaRepository<SeatSession, Long> {

    /** The student's open (ACTIVE or ON_BREAK) session, if any. At most one exists (V2 index). */
    @Query("select s from SeatSession s where s.studentId = :studentId and s.status <> com.seatsense.session.SessionStatus.ENDED")
    Optional<SeatSession> findOpenByStudent(@Param("studentId") Long studentId);

    /** The open session on a seat, if any. At most one exists (V2 index). */
    @Query("select s from SeatSession s where s.seatId = :seatId and s.status <> com.seatsense.session.SessionStatus.ENDED")
    Optional<SeatSession> findOpenBySeat(@Param("seatId") Long seatId);

    /** Open sessions on every floor with their seat codes. */
    @Query("""
        select seat.code as seatCode, s.status as status
        from SeatSession s, Seat seat
        where seat.id = s.seatId
          and s.status <> com.seatsense.session.SessionStatus.ENDED""")
    List<OpenSeat> findAllOpenSeats();

    /** Open sessions on one floor with their seat codes, for the seat map. */
    @Query("""
        select seat.code as seatCode, s.status as status
        from SeatSession s, Seat seat
        where seat.id = s.seatId
          and seat.floorId = :floorId
          and s.status <> com.seatsense.session.SessionStatus.ENDED""")
    List<OpenSeat> findOpenSeatsOnFloor(@Param("floorId") String floorId);

    /** The student's most recent session (any status), for "Same seat as last time". */
    Optional<SeatSession> findFirstByStudentIdOrderByStartedAtDesc(Long studentId);

    /** A seat code with the status of the open session on it. */
    interface OpenSeat {
        String getSeatCode();

        SessionStatus getStatus();
    }
}
