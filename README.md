# Spring Batch Remote + Local Partitioning with RabbitMQ

### RabbitMQ Settings
For testing, you can create a free account on CloudAMQP and fill the following details in application-demo.properties
For further reference, please consider the following sections:
spring.rabbitmq.host=
spring.rabbitmq.port=
spring.rabbitmq.username=
spring.rabbitmq.password=
spring.rabbitmq.virtual-host

### Remote + Local Partition
To make both work at the same time, we need to make sure, partition names are different for both.
Also For local partition, currently added timestamp for uniqueness to the names of all the worker steps
as temporary fix for JobExecutionException: Cannot restart step from STARTED status.

### Current Behaviour
Currently it creates 4 partitions for remote workers and 8 local partitions for each worker
which means 4 servers running 32 threads will process the batch.

### Misc
Commented //@Profile("worker") to run the workers on all servers, even on that server which is master.
To avoid master server from running the worker, just uncomment it.

### Future Tasks
1. Check why timestamp needs to be added for all remote+local worker steps name
2. When something goes wrong in remote step execution, partition data from the queue should be re-inserted in the queue in AMQP.