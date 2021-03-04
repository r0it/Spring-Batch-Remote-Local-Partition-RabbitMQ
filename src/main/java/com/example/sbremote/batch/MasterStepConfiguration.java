package com.example.sbremote.batch;

import com.example.sbremote.batch.partitioner.RemotePartitioner;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.partition.PartitionHandler;
import org.springframework.batch.integration.partition.MessageChannelPartitionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.support.PeriodicTrigger;

@Configuration
public class MasterStepConfiguration {

	@Autowired
	private StepBuilderFactory masterStepBuilderFactory;

	@Autowired
	@Qualifier("remoteWorkerStep")
	private Step remoteWorkerStep;

	@Value("${batch.remote.partition: #{4}}")
	private int partition;

	@Autowired
	public JobExplorer jobExplorer;

	@Autowired
	private MessagingTemplate messagingTemplate;

	@Bean
	@Qualifier("masterStep")
	public Step masterStep() throws Exception {
		return masterStepBuilderFactory.get("masterStep").partitioner("remoteWorkerStep", new RemotePartitioner()).gridSize(partition)
				.taskExecutor(taskExecutor()).partitionHandler(partitionHandler())
				.step(remoteWorkerStep) // Cannot restart step from STARTING status
				.build();
	}

	@Bean
	public ThreadPoolTaskExecutor taskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(20);
		executor.setQueueCapacity(20);
		executor.setMaxPoolSize(20);
		executor.setThreadNamePrefix("sb-master-");
		executor.initialize();

		return executor;
	}

	@Bean
	public PartitionHandler partitionHandler() throws Exception {
		MessageChannelPartitionHandler partitionHandler = new MessageChannelPartitionHandler();
		partitionHandler.setStepName("remoteWorkerStep");
		partitionHandler.setGridSize(partition);
		partitionHandler.setMessagingOperations(messagingTemplate);
		partitionHandler.setPollInterval(5000l);
		partitionHandler.setJobExplorer(jobExplorer);
		partitionHandler.afterPropertiesSet();

		return partitionHandler;
	}

	@Bean(name = PollerMetadata.DEFAULT_POLLER)
	public PollerMetadata defaultPoller() {
		PollerMetadata pollerMetadata = new PollerMetadata();
		pollerMetadata.setTrigger(new PeriodicTrigger(100));
		return pollerMetadata;
	}

}
