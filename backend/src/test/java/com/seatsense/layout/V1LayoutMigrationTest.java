package com.seatsense.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seatsense.PostgresIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Checks V1__layout_and_students.sql (WORKPLAN S0.5) against a real Postgres.
 * Expected numbers come from docs/layout/provisional-layout.json.
 */
class V1LayoutMigrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private int count(String sql) {
        Integer n = jdbc.queryForObject(sql, Integer.class);
        return n == null ? 0 : n;
    }

    @Test
    void createsTheFourFloorsInOrder() {
        List<String> floors = jdbc.queryForList("select id from floor order by number", String.class);
        assertThat(floors).containsExactly("GF", "1F", "2F", "3F");
    }

    @Test
    void seedsEverySeatFromTheLayout() {
        assertThat(count("select count(*) from seat")).isEqualTo(568);

        Map<String, Integer> perFloor = Map.of("GF", 120, "1F", 184, "2F", 120, "3F", 144);
        perFloor.forEach((floor, expected) ->
            assertThat(count("select count(*) from seat where floor_id = '" + floor + "'"))
                .as("seats on %s", floor)
                .isEqualTo(expected));
    }

    @Test
    void seedsZonesAndFixtures() {
        assertThat(count("select count(*) from zone")).isEqualTo(14);
        assertThat(count("select count(*) from layout_fixture")).isEqualTo(51);
        assertThat(count("select count(*) from zone where not bookable")).isEqualTo(3);
    }

    @Test
    void everySeatSitsInAZoneOnItsOwnFloor() {
        assertThat(count("""
            select count(*) from seat s join zone z on z.id = s.zone_id
            where z.floor_id <> s.floor_id""")).isZero();
    }

    @Test
    void everyBookableZoneHasSeatsAndNoOtherZoneDoes() {
        assertThat(count("""
            select count(*) from zone z
            where z.bookable and not exists (select 1 from seat s where s.zone_id = z.id)""")).isZero();
        assertThat(count("""
            select count(*) from zone z
            where not z.bookable and exists (select 1 from seat s where s.zone_id = z.id)""")).isZero();
    }

    @Test
    void seatCodesMatchFloorRowAndNumber() {
        assertThat(count("""
            select count(*) from seat
            where code <> floor_id || '-' || row_label || seat_number""")).isZero();
    }

    @Test
    void rowLettersSkipIAndO() {
        assertThat(count("select count(*) from seat where row_label in ('I', 'O')")).isZero();
    }

    @Test
    void tableSeatsShareTheirTableWithAtLeastOneOtherSeat() {
        assertThat(count("""
            select count(*) from (
              select table_id from seat where table_id is not null
              group by table_id having count(*) < 2) lonely""")).isZero();
    }

    @Test
    void everySeatStartsInServiceWithVersionZero() {
        assertThat(count("select count(*) from seat where out_of_service or version <> 0")).isZero();
    }

    @Test
    void seedsFortySampleStudentsAndNoRealOnes() {
        assertThat(count("select count(*) from student")).isEqualTo(40);
        assertThat(count("select count(*) from student where not is_sample")).isZero();
        assertThat(count("select count(*) from student where card_id not like 'SAMPLE%'")).isZero();
    }

    @Test
    void openingHoursAreLeftUnsetUntilTheSurvey() {
        assertThat(count("select count(*) from floor where open_time is not null or close_time is not null"))
            .isZero();
    }

    @Test
    void twoSeatsCannotShareAGridCell() {
        assertThatThrownBy(() -> jdbc.update("""
            insert into seat (code, floor_id, zone_id, row_label, seat_number, gx, gy)
            select 'GF-TEST1', floor_id, zone_id, 'Z', 99, gx, gy from seat where code = 'GF-A1'"""))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void zoneTypeIsRestrictedToTheThreeKinds() {
        assertThatThrownBy(() -> jdbc.update("""
            insert into zone (id, floor_id, section_code, name, type, note, bookable, from_row, to_row, sort_order)
            values ('GF-test', 'GF', 'test', 'Test', 'LOUD', '', true, 0, 0, 99)"""))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
