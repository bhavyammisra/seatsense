package com.seatsense.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.seatsense.PostgresIntegrationTest;
import com.seatsense.analytics.Availability.FloorAvailability;
import com.seatsense.common.SeatStateEvent;
import com.seatsense.session.SeatState;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** GET /api/public/availability and /topic/availability against the V1 seed data. */
class AvailabilityApiTest extends PostgresIntegrationTest {

    record TestSeatEvent(String seatCode, String floorId, SeatState newState, Instant at) implements SeatStateEvent {
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEventPublisher events;

    @LocalServerPort
    private int port;

    private StompSession stomp;

    @AfterEach
    void cleanUp() {
        jdbc.update("update seat set out_of_service = false where code in ('3F-A1', '3F-A2')");
        if (stomp != null && stomp.isConnected()) {
            stomp.disconnect();
        }
    }

    private Availability get() {
        ResponseEntity<Availability> response = rest.getForEntity("/api/public/availability", Availability.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @Test
    void countsEverySeatPerFloorWithoutLogin() {
        Availability now = get();

        assertThat(now.total()).isEqualTo(568);
        assertThat(now.floors()).extracting(FloorAvailability::id).containsExactly("GF", "1F", "2F", "3F");
        Map<String, Integer> totals = now.floors().stream()
            .collect(Collectors.toMap(FloorAvailability::id, FloorAvailability::total));
        assertThat(totals).containsExactlyInAnyOrderEntriesOf(Map.of("GF", 120, "1F", 184, "2F", 120, "3F", 144));
        assertThat(now.floors()).extracting(FloorAvailability::name).contains("Ground floor", "Third floor");
        assertThat(now.updatedAt()).isNotNull();
        assertThat(now.free()).isEqualTo(now.floors().stream().mapToInt(FloorAvailability::free).sum());
    }

    @Test
    void outOfServiceSeatsAreNotFreeButStillCountInTheTotal() {
        int before = get().floors().get(3).free();

        jdbc.update("update seat set out_of_service = true where code in ('3F-A1', '3F-A2')");
        FloorAvailability third = get().floors().get(3);

        assertThat(third.total()).isEqualTo(144);
        assertThat(third.free()).isEqualTo(before - 2);
        assertThat(third.full()).isFalse();
    }

    @Test
    void timesAreInLibraryTime() {
        String json = rest.getForObject("/api/public/availability", String.class);
        assertThat(json).contains("+05:30");
    }

    @Test
    void anyoneWatchingGetsFreshCountsAfterASeatChange() throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new ByteArrayMessageConverter() {
            @Override
            protected boolean supportsMimeType(MessageHeaders headers) {
                return true;
            }
        });
        String token = rest.getForEntity("/actuator/health", String.class).getHeaders()
            .getOrDefault(HttpHeaders.SET_COOKIE, List.of()).stream()
            .filter(c -> c.startsWith("XSRF-TOKEN="))
            .map(c -> c.substring("XSRF-TOKEN=".length(), c.indexOf(';')))
            .findFirst().orElseThrow();
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        handshake.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + token);
        StompHeaders connect = new StompHeaders();
        connect.add("X-XSRF-TOKEN", token);
        stomp = client.connectAsync("ws://localhost:" + port + "/ws", handshake, connect,
            new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);

        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        stomp.subscribe(AvailabilityBroadcaster.TOPIC, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });

        // The subscription registers asynchronously, so keep announcing a change until a message arrives.
        String message = null;
        long end = System.currentTimeMillis() + 5000;
        while (message == null && System.currentTimeMillis() < end) {
            events.publishEvent(new TestSeatEvent("1F-A1", "1F", SeatState.TAKEN, Instant.now()));
            message = received.poll(600, TimeUnit.MILLISECONDS);
        }

        assertThat(message).contains("\"total\":568", "\"floors\":[", "\"id\":\"GF\"");
    }
}
