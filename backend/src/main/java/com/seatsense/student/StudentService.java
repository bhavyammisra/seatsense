package com.seatsense.student;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds the student behind a scanned ID card (WORKPLAN S1.2). The gate (S1.5)
 * calls this for every scan and answers 404 UNKNOWN_CARD when it finds nothing.
 */
@Service
public class StudentService {

    /** Matches the card_id column size in V1. */
    static final int MAX_CARD_ID_LENGTH = 32;

    private final StudentRepository students;

    public StudentService(StudentRepository students) {
        this.students = students;
    }

    /**
     * Looks up a student by the number read from their card.
     *
     * <p>Barcode scanners that act as a keyboard usually send a line break after
     * the number, and people typing it by hand add spaces, so surrounding
     * whitespace is ignored. Blank or impossibly long input finds nobody instead
     * of failing.
     */
    @Transactional(readOnly = true)
    public Optional<Student> findByCardId(String scanned) {
        String cardId = normalise(scanned);
        if (cardId == null) {
            return Optional.empty();
        }
        return students.findByCardId(cardId);
    }

    static String normalise(String scanned) {
        if (scanned == null) {
            return null;
        }
        String cardId = scanned.strip();
        if (cardId.isEmpty() || cardId.length() > MAX_CARD_ID_LENGTH) {
            return null;
        }
        return cardId;
    }
}
