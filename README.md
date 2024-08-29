# Stock App
Spring Boot application responsible for displaying the price of stock shares in real time.

It uses an event-driven architecture with Kafka, integrated into Spring, to provide a continuous stream of data. In addition, the entire application features a microservices structure. The data is displayed using the HighCharts library.

We obtain the information from the Polygon.io API using Spring WebFlux.

Since we cannot get the current stock values ​​for free, and we also have limitations on the number of times per minute that the API can be called, the system then takes care of fetching all the information for the year 2023 for each stock that is requested, storing it, and providing it using Kafka. That data will then be distributed and received by Kafka producers/consumers.

The main objective of this application is to implement a data distribution architecture different from the typical REST one. That is why the event-driven architecture with Kafka. Plus, to build a microservice architecture for the entire app.

## Microservices Structure

The application is composed of several interconnected microservices:
-   [FRONT SERVER](https://github.com/pedrofanelli/stockapp-frontserver) (localhost:8091)
-   [EUREKA SERVER](https://github.com/pedrofanelli/stockapp-eurekaserver) (*Service Discovery*) (localhost:8761)
-   [GATEWAY SERVER](https://github.com/pedrofanelli/stockapp-gatewayserver) (localhost:8072)
-   [CONFIG SERVER](https://github.com/pedrofanelli/stockapp-configserver) (localhost:8071)
-   [API-CONNECTION SERVER](https://github.com/pedrofanelli/stockapp-apiconnection) (localhost:8085)

#### EUREKA SERVER
Acts as a service registry, where different microservices can register themselves with their addresses (IP/port). Eureka helps in dynamic scaling by keeping track of available instances, making it easier to distribute load and ensure high availability.

#### GATEWAY SERVER
Serves as a single entry point for client requests and routes them to the appropriate microservices. It works in conjunction with Eureka by discovering service instances dynamically, meaning it knows where to forward requests based on the service registry.

#### CONFIG SERVER
Centralizes the configuration files of the different microservices. The files are uploaded on GitHub, and are taken directly from there. Any updates made to them will be applied.

#### FRONT SERVER
Responsible for containing the Front of the application. The connection for the live update is done using SSE emitters.

#### API-CONNECTION SERVER
Used for all interaction with the API. Mainly to obtain the information required by the user of a certain stock, store it, and provide it in an event-driven manner with Kafka, gradually.

## Event-Driven Architecture with Kafka
In the introduction we made a brief explanation of the architecture, here we will expand further.  

The idea is to simulate a **continuous stream of data** with the tools available, Kafka and a limited source of data (Polygon API). Limited because we are able to use the API endpoints a couple of times per minute. In addition, it is only possible to access historical information for free, not current information.

That being so, we’ve developed a microservice entirely dedicated to this work: [API-CONNECTION SERVER](https://github.com/pedrofanelli/stockapp-apiconnection).

We’ve integrated Kafka into Spring Boot, building both Producer and Consumer Beans in the same microservice. One emits the data, and the other receives it.

The dependencies related to Kafka are: **“spring-cloud-stream-binder-kafka”** and **“spring-kafka”**. All the dependencies can be seen in the POM xml: *stockapp-apiconnection/pom.xml*.

Firstly, we had to create the configuration for the **Spring Cloud Stream Kafka Binder**. The broker is set at *localhost:9092* (the default one). The bindings are *producerBinding-out-0* for the output/producer channel and *consumerBinding-in-0* for the input/consumer channel. Then we define the Topic to connect them as *"stock-topic"*. We’ve also set the time for each producer cycle at 15 seconds.

This configuration resides in the [CONFIG SERVER](https://github.com/pedrofanelli/stockapp-configserver) microservice: *stockapp-configserver/src/main/resources/config/stockapp-apiconnection.yml*.

Secondly, we needed to build the connection to the API. For this we use **Spring WebFlux**. Using a WebClient Bean, setting the URL and credentials, we can then use it to access the API. The location for this is in the **Spring Boot Project Configuration**: *stockapp-apiconnection/src/main/java/com/stockapp/main/config/ProjectConfig.java*.

A quick note here, the API credential is stored in a private `.env` file that’s not pushed to GitHub for security reasons. The app reads that file and the WebClient uses its value. The configuration for this is in the Config Server Microservice.

Thirdly, we’ve also included the Kafka Supplier and Consumer in the Project Config. The Supplier will search for any **SSE (Server-Sent-Events)** emitters available to send data to the Clients. If there’s at least one, it will search for stored data, and send it to the Consumer.

The SSE architecture is handled as follows. From the [FRONT SERVER](https://github.com/pedrofanelli/stockapp-frontserver) microservice (*stockapp-frontserver/src/main/resources/static/front/Main.js*), the Client builds an **EventSource**: 
`new EventSource('http://localhost:8072/apiconnection/emitter/'+nameDataTicker)` 
From that endpoint a SseEmitter is sent. This emitter will be stored, so then Kafka can send data to it. In this way, we can create a *persistent connection between the front and the back, with a constant flow of data*.

The first time a Client searches for a particular Stock, the app will search for all the data for the year 2023 in the API, store it, and then Kafka will start sending data, day by day, each 15 seconds, to every available emitter related to that Stock. If another user wants to see the values of the same Stock, it WON'T use the API again, it will create another emitter, to which Kafka will send the data.

The Producer, when there's available emitters, will search for the data, and send it to the Consumer. Then, the Consumer will receive the data, and send it to every related emitter.

In this way, the api-connection microservice has two main objectives. One is to fetch the initial data when the Client first opens the page (the data may already exist or an API lookup may be required). The other is the Kafka system, looking for SSE emitters, and sending small pieces of data to each of them, continuously over time.

## Highcharts Library
When the Client receives the data, it needs to be processed into a certain format so that the chart can read and display it.

This logic is seen here: *stockapp-frontserver/src/main/resources/static/front/Main.js*.

## Endpoints
-   `localhost:8072/front/main`
	Uses the gateway to access the **main page** (using the Front Microservice).
-   `localhost:8072/front/main/{ticker}`
	View with a particular Stock.
-   `localhost:8072/apiconnection/livegraph/{ticker}`
	Back response for initial get.
-   `localhost:8072/apiconnection/emitter/{ticker}`
	Back response for Kafka data.

## Technologies
-   Spring Boot
-   Kafka
