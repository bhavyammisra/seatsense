package com.seatsense.waitlist;

import com.seatsense.auth.AppUserRepository;
import com.seatsense.waitlist.WaitlistEvents.Notify;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends waitlist news to one student's portal over /user/queue/notifications, after the change is saved. */
@Component
class WaitlistNotifier {

    private final AppUserRepository users;
    private final SimpMessagingTemplate broker;

    WaitlistNotifier(AppUserRepository users, SimpMessagingTemplate broker) {
        this.users = users;
        this.broker = broker;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void send(Notify notify) {
        users.findByStudentId(notify.studentId())
            .ifPresent(user -> broker.convertAndSendToUser(user.getUsername(), "/queue/notifications", notify.payload()));
    }
}
