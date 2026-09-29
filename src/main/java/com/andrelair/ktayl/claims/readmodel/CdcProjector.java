package com.andrelair.ktayl.claims.readmodel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamSubscription;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Slice D CDC projector (profile {@code readmodel}). A durable JetStream push-consumer on
 * {@code CLAIMS_CDC} filtered to {@code claims-cdc.globalcore.gc_claim}: each record is decoded and
 * upserted into the read-model, then explicitly acked. {@code DeliverPolicy.All} + a durable name
 * means a fresh deploy replays the whole backlog (bootstrapping the projection) while a restart
 * resumes from the last ack. Explicit ack + nak-on-failure gives at-least-once with the upsert's
 * idempotency ({@link ClaimProjectionService}) covering redelivery.
 */
@Component
@Profile("readmodel")
public class CdcProjector implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(CdcProjector.class);

    private final Connection nats;
    private final ClaimProjectionService projection;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String stream;
    private final String subject;
    private final String durable;

    private static final Duration RETRY_DELAY = Duration.ofSeconds(10);
    private final ScheduledExecutorService retryer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cdc-projector-retry");
        t.setDaemon(true);
        return t;
    });

    private Dispatcher dispatcher;
    private JetStreamSubscription subscription;
    private volatile boolean running;

    public CdcProjector(
            Connection nats,
            ClaimProjectionService projection,
            @Value("${cdc.stream:CLAIMS_CDC}") String stream,
            @Value("${cdc.subject:claims-cdc.globalcore.gc_claim}") String subject,
            @Value("${cdc.durable:claims-projector}") String durable) {
        this.nats = nats;
        this.projection = projection;
        this.stream = stream;
        this.subject = subject;
        this.durable = durable;
    }

    @Override
    public void start() {
        // NON-FATAL + self-retrying (deliberate). The projector binds a JetStream push DURABLE, which
        // allows only ONE active subscriber. During a rolling update both the old and new pod run
        // briefly, so the new pod's bind fails with [SUB-90012] "already bound" — if that threw, the
        // new pod would never become Ready, the old would never terminate to release the durable, and
        // the roll would DEADLOCK. Instead we start Ready regardless and retry the bind in the
        // background: whichever pod holds the singleton durable projects, the rest stand by and take
        // over when it's released (a natural leader-election that also makes N>1 replicas safe). A
        // stale read-model is a degraded read, not an outage — the ACL API must not depend on it.
        this.running = true;
        attemptSubscribe();
    }

    private synchronized void attemptSubscribe() {
        if (!running || subscription != null) {
            return;
        }
        try {
            JetStream js = nats.jetStream();
            ConsumerConfiguration cc = ConsumerConfiguration.builder()
                    .ackPolicy(AckPolicy.Explicit)
                    .deliverPolicy(DeliverPolicy.All)
                    .filterSubject(subject)
                    .build();
            PushSubscribeOptions options = PushSubscribeOptions.builder()
                    .stream(stream)
                    .durable(durable)
                    .configuration(cc)
                    .build();
            this.dispatcher = nats.createDispatcher();
            // autoAck=false → we ack only after a successful projection (at-least-once).
            this.subscription = js.subscribe(subject, dispatcher, this::onMessage, false, options);
            log.info("CDC projector subscribed: stream={} subject={} durable={}", stream, subject, durable);
        } catch (Exception e) {
            // e.g. [SUB-90012] already bound (another pod holds the durable during a roll) or NATS
            // unreachable. Clean up any half-created dispatcher and retry — do NOT fail app startup.
            if (dispatcher != null) {
                try {
                    nats.closeDispatcher(dispatcher);
                } catch (Exception ignore) {
                    // best effort
                }
                dispatcher = null;
            }
            if (running) {
                log.warn("CDC projector not subscribed yet ({}); retrying in {}s", e.getMessage(),
                        RETRY_DELAY.getSeconds());
                retryer.schedule(this::attemptSubscribe, RETRY_DELAY.getSeconds(), TimeUnit.SECONDS);
            }
        }
    }

    private void onMessage(io.nats.client.Message msg) {
        try {
            JsonNode value = objectMapper.readTree(msg.getData());
            projection.project(value);
            msg.ack();
        } catch (Exception e) {
            // Don't ack — JetStream will redeliver; the upsert is idempotent so a retry is safe.
            log.error("CDC projection failed (will be redelivered): {}", e.getMessage());
            try {
                msg.nak();
            } catch (Exception ignored) {
                // best effort
            }
        }
    }

    @Override
    public void stop() {
        running = false;
        retryer.shutdownNow();
        try {
            if (subscription != null) {
                subscription.unsubscribe();
            }
        } catch (Exception e) {
            log.warn("error unsubscribing CDC projector: {}", e.getMessage());
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Start after the DB/JPA is ready (default phase) but as a normal lifecycle bean. */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }
}
