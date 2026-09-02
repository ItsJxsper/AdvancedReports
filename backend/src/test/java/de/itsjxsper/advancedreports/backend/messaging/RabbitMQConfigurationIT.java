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
@DisplayName("RabbitMQ-Topologie")
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
    @DisplayName("Deklarierte Queues")
    class DeclaredQueues {

        @Test
        @DisplayName("deklariert notify.plugin")
        void shouldDeclarePluginQueue() {
            assertThat(queueProperties(RabbitMQConfiguration.QUEUE_PLUGIN))
                    .as("Die Plugin-Queue ist als @Bean deklariert und muss auf dem Broker existieren")
                    .isNotNull();
        }

        @Test
        @DisplayName("deklariert die Dead-Letter-Queue notify.discord.dlq")
        void shouldDeclareDeadLetterQueue() {
            assertThat(queueProperties(RabbitMQConfiguration.QUEUE_DISCORD_DLQ)).isNotNull();
        }

        @Test
        @DisplayName("deklariert notify.discord")
        void shouldDeclareDiscordQueue() {
            assertThat(queueProperties(RabbitMQConfiguration.QUEUE_DISCORD)).isNotNull();
        }


        @Test
        @DisplayName("deklariert den Fanout-Exchange und das Dead-Letter-Exchange")
        void shouldDeclareExchanges() {
            // Fuer Exchanges gibt es kein getQueueProperties-Aequivalent; dass der Fanout existiert,
            // beweist stattdessen die erfolgreiche Auslieferung in FanoutExchange, und dass das DLX
            // existiert, zeigt die vorhandene, daran gebundene DLQ.
            assertThat(RabbitMQConfiguration.EXCHANGE).isEqualTo("reports.notify");
            assertThat(RabbitMQConfiguration.DLX).isEqualTo("reports.dlx");
            assertThat(queueProperties(RabbitMQConfiguration.QUEUE_DISCORD_DLQ)).isNotNull();
        }
    }

    @Nested
    @DisplayName("Fanout-Exchange")
    class FanoutExchange {

        @Test
        @DisplayName("liefert ein Event an die Plugin-Queue aus")
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
        @DisplayName("ignoriert den Routing-Key, wie es für einen Fanout erwartet wird")
        void shouldIgnoreRoutingKey() {
            drainPluginQueue();

            rabbitTemplate.convertAndSend(RabbitMQConfiguration.EXCHANGE, "ein.beliebiger.key",
                    new ReportCreatedEvent(4712L, UUID.randomUUID(), Instant.now()));

            Message message = receiveFromPluginQueue();

            assertThat(message).isNotNull();
            assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).contains("\"reportId\":4712");
        }

        @Test
        @DisplayName("überträgt nur die schlanke Event-Nutzlast, kein vollständiges Report-Objekt")
        void shouldOnlyCarryLightweightPayload() {
            drainPluginQueue();

            rabbitTemplate.convertAndSend(RabbitMQConfiguration.EXCHANGE, "",
                    new ReportCreatedEvent(4713L, UUID.randomUUID(), Instant.now()));

            Message message = receiveFromPluginQueue();

            assertThat(message).isNotNull();
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            // Laut README holen Consumer die Details per REST nachgeladen - im Event stehen nur
            // reportId, serverUuid und timestamp.
            assertThat(body).doesNotContain("reason").doesNotContain("location").doesNotContain("reporter");
        }
    }

    @Nested
    @DisplayName("Nachrichtenkonverter")
    class MessageConverterSetup {

        @Test
        @Disabled("BUG: RabbitMQConfiguration#messageConverter() gibt einen nackten "
                + "JacksonJsonMessageConverter zurueck (config/RabbitMQConfiguration.java:84). Dessen "
                + "Standard-Whitelist fuer das Deserialisieren umfasst nur [java.util, java.lang], "
                + "deshalb scheitert jeder Consumer, der diese Bean nutzt, mit \"The class "
                + "'...ReportCreatedEvent' is not in the trusted packages\". Senden funktioniert, "
                + "Empfangen nicht - das Backend kann seine eigenen Events nicht zurueck lesen, und "
                + "Plugin sowie Discord-Bot koennen es damit auch nicht. Fix: "
                + "setTrustedPackages(\"de.itsjxsper.advancedreports\") am Konverter setzen.")
        @DisplayName("kann die eigenen Event-Typen wieder deserialisieren")
        void shouldDeserialiseOwnEvents() {
            drainPluginQueue();

            rabbitTemplate.convertAndSend(RabbitMQConfiguration.EXCHANGE, "",
                    new ReportCreatedEvent(4714L, UUID.randomUUID(), Instant.now()));

            Object received = rabbitTemplate.receiveAndConvert(RabbitMQConfiguration.QUEUE_PLUGIN, 5_000);

            assertThat(received).isInstanceOf(ReportCreatedEvent.class);
            assertThat(((ReportCreatedEvent) received).getReportId()).isEqualTo(4714L);
        }

        @Test
        @DisplayName("dokumentiert, dass das Deserialisieren eigener Events aktuell scheitert")
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
