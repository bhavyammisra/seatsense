package com.seatsense.student;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.stream.Collectors;

/** A student, found by the number on their ID card (table {@code student}, V1). */
@Entity
@Table(name = "student")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_id", nullable = false, unique = true, length = 32)
    private String cardId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 80)
    private String programme;

    @Column(name = "year")
    private Short year;

    @Column(name = "is_sample", nullable = false)
    private boolean sample;

    protected Student() {
        // for JPA
    }

    public Student(String cardId, String name, boolean sample) {
        this.cardId = cardId;
        this.name = name;
        this.sample = sample;
    }

    public Long getId() {
        return id;
    }

    public String getCardId() {
        return cardId;
    }

    public String getName() {
        return name;
    }

    public String getProgramme() {
        return programme;
    }

    public Short getYear() {
        return year;
    }

    /** True for demo and test data, never a real person. */
    public boolean isSample() {
        return sample;
    }

    /** Up to two letters for the kiosk avatar, for example "Aarav S." gives "AS". */
    public String getInitials() {
        return initialsOf(name);
    }

    static String initialsOf(String name) {
        if (name == null) {
            return "";
        }
        return Arrays.stream(name.trim().split("\\s+"))
            .filter(word -> !word.isEmpty() && Character.isLetter(word.charAt(0)))
            .limit(2)
            .map(word -> String.valueOf(Character.toUpperCase(word.charAt(0))))
            .collect(Collectors.joining());
    }
}
