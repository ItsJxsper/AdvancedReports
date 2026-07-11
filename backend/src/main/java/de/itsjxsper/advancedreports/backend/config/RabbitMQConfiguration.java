package de.itsjxsper.advancedreports.backend.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfiguration {

    public static final String EXCHANGE = "reports.notify";
    public static final String QUEUE_PLUGIN = "notify.plugin";
    public static final String QUEUE_DISCORD = "notify.discord";
    public static final String DLX = "reports.dlx";
    public static final String QUEUE_DISCORD_DLQ = "notify.discord.dlq";

    @Bean
    public FanoutExchange reportsNotifyExchange() {
        return ExchangeBuilder
                .fanoutExchange(EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder
                .directExchange(DLX)
                .durable(true)
                .build();
    }

    @Bean
    public Queue notifyPluginQueue() {
        return QueueBuilder
                .durable(QUEUE_PLUGIN)
                .build();
    }

    public Queue notifyDiscordQueue() {
        return QueueBuilder
                .durable(QUEUE_DISCORD)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_DISCORD_DLQ)
                .build();
    }

    @Bean
    public Queue discordDeadLetterQueue() {
        return QueueBuilder
                .durable(QUEUE_DISCORD_DLQ)
                .build();
    }

    @Bean
    public Binding pluginBinding() {
        return BindingBuilder
                .bind(notifyPluginQueue())
                .to(reportsNotifyExchange());
    }

    @Bean
    public Binding discordBinding() {
        return BindingBuilder
                .bind(notifyDiscordQueue())
                .to(reportsNotifyExchange());
    }

    @Bean
    public Binding discordDlqBinding() {
        return BindingBuilder
                .bind(discordDeadLetterQueue())
                .to(deadLetterExchange())
                .with(QUEUE_DISCORD_DLQ);
    }


    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        template.setMandatory(true);
        return template;
    }
}