package com.example.sbremote.batch;

import com.example.sbremote.batch.partitioner.LocalPartitioner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.integration.partition.BeanFactoryStepLocator;
import org.springframework.batch.integration.partition.StepExecutionRequestHandler;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.H2PagingQueryProvider;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.integration.amqp.inbound.AmqpInboundChannelAdapter;
import org.springframework.integration.annotation.Poller;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.sql.DataSource;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class WorkerStepConfiguation {

    @Autowired
    private StepBuilderFactory stepBuilderFactory;

    @Autowired
    public JobExplorer jobExplorer;

    @Autowired
    DataSource dataSource;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    @Qualifier("inboundRequests")
    private QueueChannel inboundRequests;

    @Autowired
    private ItemReader<Customer> crItemReader;

    @Autowired
    private ItemWriter<Customer> crItemWriter;

    @Autowired
    private LocalPartitioner localPartitioner;

    @Bean("crItemReader")
    @StepScope
    public ItemReader<Customer> reader(@Value("#{stepExecutionContext['locStartIdx']}") Long startIdx,
                                       @Value("#{stepExecutionContext['locEndIdx']}") Long endIdx){
        log.info("Local Reader- locStartIdx: {}, locEndIdx: {}", startIdx, endIdx);
        Map<String, Order> sortKeys = new HashMap<>();
        sortKeys.put("id", Order.ASCENDING);
        H2PagingQueryProvider queryProvider = new H2PagingQueryProvider();
        queryProvider.setSelectClause("SELECT id, name");
        queryProvider.setFromClause("FROM customers");
        queryProvider.setWhereClause("id >= " + startIdx + " and id <= " + endIdx);
        queryProvider.setSortKeys(sortKeys);
        return new JdbcPagingItemReaderBuilder<Customer>()
                .name("pagingItemReader")
                .dataSource(dataSource)
                .pageSize(10)
                .queryProvider(queryProvider)
                .rowMapper(new BeanPropertyRowMapper<>(Customer.class))
                .build();
    }

    @Bean("crItemWriter")
    @StepScope
    public JdbcBatchItemWriter<Customer> itemWriter() {
        return new JdbcBatchItemWriterBuilder<Customer>()
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<Customer>())
                .sql("UPDATE customers set name=:name where id=:id")
                .dataSource(dataSource)
                .build();
    }

    @Bean
    public ThreadPoolTaskExecutor workerTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setQueueCapacity(20);
        executor.setMaxPoolSize(20);
        executor.setThreadNamePrefix("sb-worker-");
        executor.initialize();

        return executor;
    }

    @Bean
    public Step remoteWorkerStep() throws Exception {
        return stepBuilderFactory.get("remoteWorkerStep"
                +System.currentTimeMillis() //temporary fix for JobExecutionException: Cannot restart step from STARTED status
        )
                .allowStartIfComplete(true)
                .listener(localPartitioner)
                .partitioner("localWorkerStep", localPartitioner)
                .step(localWorkerStep())
                .gridSize(8)
                .taskExecutor(workerTaskExecutor())
                .build();
    }

    @Bean
    public Step localWorkerStep() {
        return stepBuilderFactory.get("localWorkerStep"
                +System.currentTimeMillis() //temporary fix for JobExecutionException: Cannot restart step from STARTED status
        )
                .allowStartIfComplete(true)
                .<Customer, Customer>chunk(10)
                .reader(crItemReader)
                .processor(new ItemProcessor<Customer, Customer>() {
                    @Override
                    public Customer process(Customer customer) throws Exception {
                        log.info("id: {}", customer.getId());
                        return customer;
                    }
                })
                .writer(crItemWriter)
                .build();
    }

//    @Bean
//    public Step localWorkerStep() {
//        return stepBuilderFactory.get("localWorkerStep"
//                +System.currentTimeMillis() //temporary fix for JobExecutionException: Cannot restart step from STARTED status
//        )
//                .allowStartIfComplete(true)
//                .tasklet(new Tasklet() {
//                    @Override
//                    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
//                        log.info("Local Partition- locStartIdx: {}, locEndIdx: {}", chunkContext.getStepContext().getStepExecution().getExecutionContext()
//                        .getLong("locStartIdx"), chunkContext.getStepContext().getStepExecution().getExecutionContext()
//                                .getLong("locEndIdx"));
//                        return RepeatStatus.FINISHED;
//                    }
//                })
//                .build();
//    }

    @Bean
//    @Profile({"worker"})
    @ServiceActivator(inputChannel = "inboundRequests", outputChannel = "outboundStaging",
            async = "true",
            poller = {@Poller(fixedRate = "1000")})
    public StepExecutionRequestHandler stepExecutionRequestHandler() {
        StepExecutionRequestHandler stepExecutionRequestHandler = new StepExecutionRequestHandler();
        BeanFactoryStepLocator stepLocator = new BeanFactoryStepLocator();
        stepLocator.setBeanFactory(this.applicationContext);
        stepExecutionRequestHandler.setStepLocator(stepLocator);
        stepExecutionRequestHandler.setJobExplorer(this.jobExplorer);

        return stepExecutionRequestHandler;
    }

    @Bean
//    @Profile({"worker"})
    public AmqpInboundChannelAdapter inbound(SimpleMessageListenerContainer listenerContainer) {
        AmqpInboundChannelAdapter adapter = new AmqpInboundChannelAdapter(listenerContainer);
        adapter.setOutputChannel(inboundRequests);
        adapter.afterPropertiesSet();

        return adapter;
    }

    @Bean
    public SimpleMessageListenerContainer container(ConnectionFactory connectionFactory) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames("sbr1");
        // container.setConcurrency("10");
        // container.setConcurrentConsumers(1);
        container.setErrorHandler(t -> {
            log.error("message listener error," + t.getMessage());
        });
        container.setAutoStartup(false);

        return container;
    }
}
