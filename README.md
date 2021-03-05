# Spring Batch Remote + Local Partitioning with RabbitMQ
A cloud ready app for extreme batch processing using N servers and N threads for each server. I have achieved Remote + Local partitioning using RabbitMQ.
<br>eg. you can run this app on 4 servers and each server can have 8 threads. So total 32 threads are processing the batches in parallel.
### RabbitMQ Settings
For testing, you can create a free account on CloudAMQP and fill the following details in application-demo.properties
For further reference, please consider the following sections:
<br>spring.rabbitmq.host=
<br>spring.rabbitmq.port=
<br>spring.rabbitmq.username=
<br>spring.rabbitmq.password=
<br>spring.rabbitmq.virtual-host=

### How to run
1. Create a free account on CloudAMQP and add all rabbitmq details.
2. Since it's using h2 db. You can start the application and go to localhost:8080/h2-console and add following details, then click on connect.
<br>Driver class: org.h2.Driver
<br>jdbcURL: jdbc:h2:./data/sbremote
<br>username: sa
<br>pwd: blank
3. Run the SQL script provided in data.sql file.
4. Using intelliJ you can run the application parallely by providing different ports in the variables.
5. just open http://localhost:8080 to start the job.


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