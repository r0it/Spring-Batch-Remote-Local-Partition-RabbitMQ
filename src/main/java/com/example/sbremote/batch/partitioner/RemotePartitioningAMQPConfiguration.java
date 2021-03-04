package com.example.sbremote.batch.partitioner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.AsyncRabbitTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.amqp.outbound.AsyncAmqpOutboundGateway;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.NullChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.messaging.PollableChannel;

@Configuration
@EnableIntegration
@Slf4j
public class RemotePartitioningAMQPConfiguration {

	@Bean
	@ServiceActivator(inputChannel = "outboundRequests")
	public AsyncAmqpOutboundGateway amqpOutboundEndpoint(AsyncRabbitTemplate asyncTemplate) {
		AsyncAmqpOutboundGateway gateway = new AsyncAmqpOutboundGateway(asyncTemplate);
		gateway.setExchangeName("user-service.batch");
		gateway.setRoutingKey("sbr1");
		gateway.setRequiresReply(false);

		log.info("Created AMQP Outbound gateway---> " + gateway.toString());
		return gateway;
	}

	@Bean
	public AsyncRabbitTemplate asyncTemplate(RabbitTemplate rabbitTemplate) {
		return new AsyncRabbitTemplate(rabbitTemplate);
	}

	@Bean
	public QueueChannel inboundRequests() {
		return new QueueChannel();
	}

	@Bean
	public DirectChannel outboundRequests() {
		return new DirectChannel();
	}

	@Bean
	public PollableChannel outboundStaging() {
		return new NullChannel();
	}

	@Bean
	public Queue requestQueue() {
		return new Queue("sbr1", true);
	}

	@Bean
	Binding binding(Queue queue, TopicExchange exchange) {
		return BindingBuilder.bind(queue).to(exchange).with("sbr1");
	}

	@Bean
	public TopicExchange batch() {
		return new TopicExchange("user-service.batch", true, false);
	}

	@Bean
	public MessagingTemplate messageTemplate() {
		MessagingTemplate messagingTemplate = new MessagingTemplate(outboundRequests());
		messagingTemplate.setReceiveTimeout(200000l);
		return messagingTemplate;
	}
}
