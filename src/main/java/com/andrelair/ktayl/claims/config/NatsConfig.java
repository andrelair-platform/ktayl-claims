package com.andrelair.ktayl.claims.config;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Duration;

/**
 * NATS connection for the CDC projector (profile {@code readmodel}). In-cluster NATS is open to
 * client namespaces (the {@code allow-cluster-clients} netpol), so no credentials — same anonymous
 * access Debezium's sink uses. Infinite reconnect so a NATS blip doesn't kill the projector.
 */
@Configuration
@Profile("readmodel")
public class NatsConfig {

    @Bean(destroyMethod = "close")
    public Connection natsConnection(@Value("${nats.url}") String url) throws Exception {
        Options options = new Options.Builder()
                .server(url)
                .connectionTimeout(Duration.ofSeconds(10))
                .maxReconnects(-1)
                .reconnectWait(Duration.ofSeconds(2))
                .build();
        return Nats.connect(options);
    }
}
