package com.example.sbremote.batch;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.batch.integration.partition.StepExecutionRequest;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RabbitMQConsumer {

//    @RabbitListener(queues = "sbr1")
//    public void recievedMessage(StepExecutionRequest stepExecutionRequest) {
//        System.out.println("Recieved Message From RabbitMQ: " + stepExecutionRequest.toString());
//    }
}