package com.seatsense.student;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Pure Java checks, no database. */
class StudentTest {

    @Test
    void initialsTakeTheFirstLetterOfTheFirstTwoWords() {
        assertThat(Student.initialsOf("Aarav S.")).isEqualTo("AS");
        assertThat(Student.initialsOf("Siddharth V.")).isEqualTo("SV");
        assertThat(Student.initialsOf("meera")).isEqualTo("M");
        assertThat(Student.initialsOf("Jasleen Kaur Gill")).isEqualTo("JK");
    }

    @Test
    void initialsIgnoreExtraSpacesAndNonLetters() {
        assertThat(Student.initialsOf("  Tara   H. ")).isEqualTo("TH");
        assertThat(Student.initialsOf("(Sample) Riya W.")).isEqualTo("RW");
        assertThat(Student.initialsOf("")).isEmpty();
        assertThat(Student.initialsOf(null)).isEmpty();
    }

    @Test
    void normaliseStripsWhitespaceAndRejectsBlankOrTooLong() {
        assertThat(StudentService.normalise(" SAMPLE0001\n")).isEqualTo("SAMPLE0001");
        assertThat(StudentService.normalise("   ")).isNull();
        assertThat(StudentService.normalise(null)).isNull();
        assertThat(StudentService.normalise("x".repeat(32))).hasSize(32);
        assertThat(StudentService.normalise("x".repeat(33))).isNull();
    }
}
