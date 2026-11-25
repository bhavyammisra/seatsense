package com.seatsense.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seatsense.PostgresIntegrationTest;
import com.seatsense.auth.AppUser;
import com.seatsense.auth.AppUserRepository;
import com.seatsense.auth.Role;
import com.seatsense.common.GateScanEvent;
import com.seatsense.common.SeatStateEvent;
import com.seatsense.session.SeatState;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** Real browser-like STOMP connections against the running app (docs/api.md, WebSocket). */
class LiveIntegrationTest extends PostgresIntegrationTest {

    record TestSeatEvent(String seatCode, String floorId, SeatState newState, Instant at) implements SeatStateEvent {
    }

    record TestGateEvent(String gateId, Object message) implements GateScanEvent {
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    private final WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
    private StompSession session;

    /** Errors the client saw (ERROR frames, conversion problems), so a silent failure shows up in the assertion. */
    private final BlockingQueue<String> errors = new LinkedBlockingQueue<>();

    LiveIntegrationTest() {
        // Accept any content type as raw bytes: the server sends application/json.
        client.setMessageConverter(new ByteArrayMessageConverter() {
            @Override
            protected boolean supportsMimeType(org.springframework.messaging.MessageHeaders headers) {
                return true;
            }
        });
    }

    @AfterEach
    void disconnect() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    /** What a browser holds after loading a page: the XSRF-TOKEN cookie, and optionally a login session. */
    private record Browser(String csrfToken, String cookies) {
    }

    private static String cookie(ResponseEntity<?> response, String name) {
        List<String> setCookies = response.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of());
        return setCookies.stream().filter(c -> c.startsWith(name + "="))
            .map(c -> c.substring(name.length() + 1, c.indexOf(';'))).findFirst().orElse(null);
    }

    private Browser anonymous() {
        String token = cookie(rest.getForEntity("/actuator/health", String.class), "XSRF-TOKEN");
        return new Browser(token, "XSRF-TOKEN=" + token);
    }

    private Browser loggedInAs(String username, Role role) {
        if (!users.existsByUsername(username)) {
            users.save(new AppUser(username, encoder.encode("pw"), role, null));
        }
        Browser anon = anonymous();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, anon.cookies());
        headers.add("X-XSRF-TOKEN", anon.csrfToken());
        ResponseEntity<String> login = rest.postForEntity("/api/auth/login",
            new HttpEntity<>("{\"username\":\"%s\",\"password\":\"pw\"}".formatted(username), headers), String.class);
        assertThat(login.getStatusCode().is2xxSuccessful()).isTrue();
        String fresh = cookie(login, "XSRF-TOKEN");
        String token = fresh != null ? fresh : anon.csrfToken();
        return new Browser(token, "XSRF-TOKEN=" + token + "; JSESSIONID=" + cookie(login, "JSESSIONID"));
    }

    private CompletableFuture<StompSession> connect(Browser browser, boolean sendCsrf) {
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        handshake.add(HttpHeaders.COOKIE, browser.cookies());
        StompHeaders connect = new StompHeaders();
        if (sendCsrf) {
            connect.add("X-XSRF-TOKEN", browser.csrfToken());
        }
        return client.connectAsync("ws://localhost:" + port + "/ws", handshake, connect, new StompSessionHandlerAdapter() {
            @Override
            public void handleException(StompSession s, org.springframework.messaging.simp.stomp.StompCommand command,
                                        StompHeaders headers, byte[] payload, Throwable e) {
                errors.add("exception: " + e);
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                errors.add("frame: " + headers.getFirst("message"));
            }

            @Override
            public void handleTransportError(StompSession s, Throwable e) {
                errors.add("transport: " + e);
            }
        });
    }

    private BlockingQueue<String> subscribe(Browser browser, String destination) throws Exception {
        session = connect(browser, true).get(5, TimeUnit.SECONDS);
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });
        return received;
    }

    /** Publishes until a message arrives or time runs out: the subscription registers asynchronously. */
    private String publishUntilReceived(Runnable publish, BlockingQueue<String> received, long millis) throws Exception {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            publish.run();
            String message = received.poll(100, TimeUnit.MILLISECONDS);
            if (message != null) {
                return message;
            }
        }
        return null;
    }

    @Test
    void anyoneReceivesSeatChangesForTheirFloor() throws Exception {
        BlockingQueue<String> received = subscribe(anonymous(), "/topic/seats/1F");

        String message = publishUntilReceived(() -> events.publishEvent(new TestSeatEvent(
            "1F-A1", "1F", SeatState.TAKEN, Instant.parse("2026-11-26T12:30:00Z"))), received, 5000);

        assertThat(message).as("errors: %s", errors).contains("\"code\":\"1F-A1\"", "\"state\":\"TAKEN\"", "\"at\":\"2026-11-26T12:30:00Z\"");
    }

    @Test
    void seatChangesOnOtherFloorsAreNotSent() throws Exception {
        BlockingQueue<String> received = subscribe(anonymous(), "/topic/seats/GF");

        // The subscription works (a GF change arrives)...
        String own = publishUntilReceived(() -> events.publishEvent(new TestSeatEvent(
            "GF-A1", "GF", SeatState.TAKEN, Instant.now())), received, 5000);
        assertThat(own).as("errors: %s", errors).contains("\"code\":\"GF-A1\"");
        received.clear();

        // ...so silence for 1F changes means they are filtered out, not that nothing works.
        String other = publishUntilReceived(() -> events.publishEvent(new TestSeatEvent(
            "1F-A1", "1F", SeatState.FREE, Instant.now())), received, 1000);
        assertThat(other).isNull();
    }

    @Test
    void gateMessagesWithStudentNamesAreNotSentToAnonymousListeners() throws Exception {
        BlockingQueue<String> received = subscribe(anonymous(), "/topic/gate/MAIN");

        String message = publishUntilReceived(() -> events.publishEvent(new TestGateEvent(
            "MAIN", Map.of("type", "SCANNED", "student", Map.of("name", "Aarav S.")))), received, 1500);

        assertThat(message).isNull();
        // Not just silence: the server refused the subscription with an ERROR frame.
        assertThat(errors.poll(2, TimeUnit.SECONDS)).as("expected an ERROR frame").isNotNull();
    }

    @Test
    void theGateAccountReceivesScans() throws Exception {
        BlockingQueue<String> received = subscribe(loggedInAs("t.gate", Role.GATE), "/topic/gate/MAIN");

        String message = publishUntilReceived(() -> events.publishEvent(new TestGateEvent(
            "MAIN", Map.of("type", "SCANNED", "mode", "ENTRY"))), received, 5000);

        assertThat(message).as("errors: %s", errors).contains("\"type\":\"SCANNED\"", "\"mode\":\"ENTRY\"");
    }

    @Test
    void connectingWithoutTheCsrfTokenIsRefused() {
        assertThatThrownBy(() -> {
            StompSession s = connect(anonymous(), false).get(3, TimeUnit.SECONDS);
            session = s;
            // A refused CONNECT may complete and then drop at once; either way no usable session remains.
            Thread.sleep(300);
            if (!s.isConnected()) {
                throw new IllegalStateException("disconnected");
            }
        }).isNotNull();
    }
}
