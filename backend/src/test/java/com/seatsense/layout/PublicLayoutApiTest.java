package com.seatsense.layout;

import static org.assertj.core.api.Assertions.assertThat;

import com.seatsense.PostgresIntegrationTest;
import com.seatsense.layout.LayoutViews.FloorView;
import com.seatsense.layout.LayoutViews.SeatView;
import com.seatsense.session.SeatState;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** GET /api/public/floors and /api/public/floors/{id}/seats against the V1 seed data. */
class PublicLayoutApiTest extends PostgresIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    private List<FloorView> floors() {
        ResponseEntity<List<FloorView>> response = rest.exchange(
            "/api/public/floors", HttpMethod.GET, null, new ParameterizedTypeReference<>() { });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private List<SeatView> seats(String floorId) {
        ResponseEntity<List<SeatView>> response = rest.exchange(
            "/api/public/floors/" + floorId + "/seats", HttpMethod.GET, null, new ParameterizedTypeReference<>() { });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @Test
    void listsTheFourFloorsLowestFirst() {
        List<FloorView> floors = floors();

        assertThat(floors).extracting(FloorView::id).containsExactly("GF", "1F", "2F", "3F");
        FloorView first = floors.get(1);
        assertThat(first.name()).isEqualTo("First floor");
        assertThat(first.shortName()).isEqualTo("First");
        assertThat(first.gridWidth()).isPositive();
    }

    @Test
    void floorsCarryTheirSectionsInOrderAndTheirFurniture() {
        FloorView third = floors().get(3);

        assertThat(third.sections()).extracting(LayoutViews.SectionView::id)
            .containsExactly("rail", "quiet-tables", "facade", "rooms", "seminar");
        assertThat(third.sections()).filteredOn(s -> s.id().equals("rooms")).singleElement()
            .satisfies(s -> {
                assertThat(s.zone()).isEqualTo(Zone.DISCUSSION);
                assertThat(s.bookable()).isTrue();
            });
        assertThat(third.sections()).filteredOn(s -> s.id().equals("seminar")).singleElement()
            .satisfies(s -> assertThat(s.bookable()).isFalse());
        assertThat(third.fixtures()).extracting(LayoutViews.FixtureView::kind).contains("room", "area", "table");
        assertThat(third.fixtures()).allSatisfy(f -> assertThat(f.section()).isNotBlank());

        int allFixtures = floors().stream().mapToInt(f -> f.fixtures().size()).sum();
        assertThat(allFixtures).isEqualTo(51);
    }

    @Test
    void theShortNameIsSentAsShortLikeTheFrontendExpects() {
        String json = rest.getForObject("/api/public/floors", String.class);
        assertThat(json).contains("\"short\":\"Ground\"");
    }

    @Test
    void listsEverySeatOnAFloorAndAllAreFreeBeforeSessionsExist() {
        List<SeatView> seats = seats("1F");

        assertThat(seats).hasSize(184);
        assertThat(seats).allSatisfy(s -> assertThat(s.state()).isEqualTo(SeatState.FREE));
        assertThat(seats).allSatisfy(s -> assertThat(s.floor()).isEqualTo("1F"));
    }

    @Test
    void aSeatCarriesItsPositionTableAndFeatures() {
        SeatView a1 = seats("1F").stream().filter(s -> s.code().equals("1F-A1")).findFirst().orElseThrow();

        assertThat(a1.section()).isEqualTo("tables");
        assertThat(a1.zone()).isEqualTo(Zone.GENERAL);
        assertThat(a1.row()).isEqualTo("A");
        assertThat(a1.number()).isEqualTo(1);
        assertThat(a1.tableId()).isEqualTo("1F-tables-T1");
        assertThat(a1.power()).isTrue();
    }

    @Test
    void seatsComeInMapOrderTopToBottomThenLeftToRight() {
        List<SeatView> seats = seats("GF");
        for (int i = 1; i < seats.size(); i++) {
            SeatView before = seats.get(i - 1);
            SeatView after = seats.get(i);
            assertThat(before.gy() < after.gy() || (before.gy() == after.gy() && before.gx() < after.gx()))
                .as("%s before %s", before.code(), after.code())
                .isTrue();
        }
    }

    @Test
    void aSeatMarkedOutOfServiceShowsAsOutOfService() {
        jdbc.update("update seat set out_of_service = true where code = '2F-A1'");
        try {
            Map<String, SeatState> states = seats("2F").stream()
                .collect(java.util.stream.Collectors.toMap(SeatView::code, SeatView::state));
            assertThat(states.get("2F-A1")).isEqualTo(SeatState.OUT_OF_SERVICE);
            assertThat(states.get("2F-A2")).isEqualTo(SeatState.FREE);
        } finally {
            jdbc.update("update seat set out_of_service = false where code = '2F-A1'");
        }
    }

    @Test
    void unknownFloorIsNotFound() {
        ResponseEntity<String> response = rest.getForEntity("/api/public/floors/9F/seats", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
