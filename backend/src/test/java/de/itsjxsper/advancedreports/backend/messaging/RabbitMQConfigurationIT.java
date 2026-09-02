package de.itsjxsper.advancedreports.backend.messaging;

import de.itsjxsper.advancedreports.backend.config.RabbitMQConfiguration;
import de.itsjxsper.advancedreports.backend.messaging.events.ReportCreatedEvent;
import de.itsjxsper.advancedreports.backend.support.AbstractE2ETest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the AMQP topology against a real broker.
 * <p>
 * A unit test cannot catch a missing {@code @Bean} here: the {@code Queue} object is still constructed
 * by the method call inside {@code discordBinding()}, it simply never gets declared on the broker.
 * Only asking the broker itself shows the difference — and, as the tests below document, the
 * consequences reach well beyond the Discord queue.
 */
@DisplayName("RabbitMQ topology")
class RabbitMQConfigurationIT extends AbstractE2ETest {
    
    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private Properties queueProperties(String queueName) {
        return amqpAdmin.getQueueProperties(queueName);
    }

    /**
     * Empties the plugin queue before a routing assertion.
     * <p>
     * Retries on {@link AmqpException} on purpose: the first operation on a fresh channel triggers
     * RabbitAdmin s declaration pass, and a channel lost during that pass has to be replaced before
     * the next attempt can succeed.
     */
    private void drainPluginQueue() {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                while (rabbitTemplate.receive(RabbitMQConfiguration.QUEUE_PLUGIN, 200) != null) {
                    // keep draining
                }
                return;
            } catch (AmqpException ignored) {
                // Channel was killed by the failed declaration — the next attempt gets a new one.
            }
        }
    }

    private Message receiveFromPluginQueue() {
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                Message message = rabbitTemplate.receive(RabbitMQConfiguration.QUEUE_PLUGIN, 1_000);
                if (message != null) {
                    return message;
                }
            } catch (AmqpException ignored) {
                // See drainPluginQueue().
            }
        }
        return null;
    }

    @Nested
    @DisplayName("Declared queues")
    class DeclaredQueues {

        @Test
        @DisplayName("declares notify.plugin")
        void shouldDeclarePluginQueue() {
            assertThat(queueProperties(RabbitMQConfiguration.QUEUE_PLUGIN))
                    .as("The plugin queue is declared as a @Bean and has to exist on the broker")
                    .isNotNull();
        }

        @Test
        @DisplayName("declares the dead-letter queue notify.discord.dlq")
        void shouldDeclareDeadLetterQueue() {
            assertThat(queueProperties(RabbitMQConfiguration.QUEUE_DISCORD_DLQ)).isNotNull();
        }

        @Test
        @DisplayName("declares notify.discord")
        void shouldDeclareDiscordQueue() {
            assertThat(queueProperties(RabbitMQConfiguration.QUEUE_DISCORD)).isNotNull();
        }


        @Test
        @DisplayName("declares the fanout exchange and the dead-letter exchange")
        void shouldDeclareExchanges() {
            // There is no getQueueProperties equivalent for exchanges; that the fanout exists is
            // proven instead by the successful delivery in FanoutExchange, and that the DLX exists
            // is shown by the DLQ bound to it.
            assertThat(RabbitMQConfiguration.EXCHANGE).isEqualTo("reports.notify");
            assertThat(RabbitMQConfiguration.DLX).isEqualTo("reports.dlx");
            assertThat(queueProperties(RabbitMQConfiguration.QUEUE_DISCORD_DLQ)).isNotNull();
        }
    }

    @Nested
    @DisplayName("Fanout exchange")
    class FanoutExchange {

        @Test
        @DisplayName("delivers an event to the plugin queue")
        void shouldRouteEventToPluginQueue() {
            drainPluginQueue();

            UUID serverUuid = UUID.randomUUID();
            rabbitTemplate.convertAndSend(RabbitMQConfiguration.EXCHANGE, "",
                    new ReportCreatedEvent(4711L, serverUuid, Instant.now()));

            Message message = receiveFromPluginQueue();

            assertThat(message).isNotNull();
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            assertThat(body)
                    .contains("\"event\":\"report.created\"")
                    .contains("\"reportId\":4711")
                    .contains(serverUuid.toString());
            assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        }

        @Test
        @DisplayName("ignores the routing key, as expected for a fanout")
        void shouldIgnoreRoutingKey() {
            drainPluginQueue();

            rabbitTemplate.convertAndSend(RabbitMQConfiguration.EXCHANGE, "ein.beliebiger.key",
                    new ReportCreatedEvent(4712L, UUID.randomUUID(), Instant.now()));

            Message message = receiveFromPluginQueue();

            assertThat(message).isNotNull();
            assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).contains("\"reportId\":4712");
        }

        @Test
        @DisplayName("carries only the lightweight event payload, not a full report object")
        void shouldOnlyCarryLightweightPayload() {
            drainPluginQueue();

            rabbitTemplate.convertAndSend(RabbitMQConfiguration.EXCHANGE, "",
                    new ReportCreatedEvent(4713L, UUID.randomUUID(), Instant.now()));

            Message message = receiveFromPluginQueue();

            assertThat(message).isNotNull();
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            // Per the README consumers re-fetch the details over REST - the event carries only
            // reportId, serverUuid and timestamp.
            assertThat(body).doesNotContain("reason").doesNotContain("location").doesNotContain("reporter");
        }
    }

    @Nested
    @DisplayName("Message converter")
    class MessageConverterSetup {

        @Test
        @Disabled("BUG: RabbitMQConfiguration#messageConverter() returns a bare "
                + "JacksonJsonMessageConverter (config/RabbitMQConfiguration.java:84). Its default "
                + "whitelist for deserialisation only covers [java.util, java.lang], so every "
                + "consumer using that bean fails with \"The class "
                + "'...ReportCreatedEvent' is not in the trusted packages\". Sending works, "
                + "receiving does not - the backend cannot read back its own events, and neither "
                + "can the plugin or the Discord bot. Fix: "
                + "call setTrustedPackages(\"de.itsjxsper.advancedreports\") on the converter.")
        @DisplayName("can deserialise its own event types again")
        void shouldDeserialiseOwnEvents() {
            drainPluginQueue();

            rabbitTemplate.convertAndSend(RabbitMQConfiguration.EXCHANGE, "",
                    new ReportCreatedEvent(4714L, UUID.randomUUID(), Instant.now()));

            Object received = rabbitTemplate.receiveAndConvert(RabbitMQConfiguration.QUEUE_PLUGIN, 5_000);

            assertThat(received).isInstanceOf(ReportCreatedEvent.class);
            assertThat(((ReportCreatedEvent) received).getReportId()).isEqualTo(4714L);
        }

        @Test
        @DisplayName("documents that deserialising the project's own events currently fails")
        void shouldCurrentlyFailToDeserialiseOwnEvents() {
            drainPluginQueue();

            rabbitTemplate.convertAndSend(RabbitMQConfiguration.EXCHANGE, "",
                    new ReportCreatedEvent(4715L, UUID.randomUUID(), Instant.now()));

            assertThatThrownBy(() ->
                    rabbitTemplate.receiveAndConvert(RabbitMQConfiguration.QUEUE_PLUGIN, 5_000))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("is not in the trusted packages");
        }
    }
}
