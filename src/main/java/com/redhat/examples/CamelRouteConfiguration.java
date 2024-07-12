/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.examples;

import static com.redhat.examples.ApplicationConfiguration.BatchIngest.IngestType.*;

import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.List;
import java.util.Map;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class CamelRouteConfiguration extends RouteBuilder {

  private static final Logger log = LoggerFactory.getLogger(CamelRouteConfiguration.class);
  
  @Autowired
  ApplicationConfiguration config;
  
  @Autowired
  WeaviateHelper weaviateHelper;
  
  @Bean
  IdempotentRepository batchIngestHashIdempotentRepository() {
    return new MemoryIdempotentRepository();
  }
  
  @Bean
  AggregationStrategy deterministicHashHeaderEnrichmentStrategy() {
    return new HeaderEnrichmentStrategy(ApplicationHeaders.DETERMINISTIC_HASH);
  }
  
  @Override
  public void configure() {
    
    /*
     * Determine which routes should start.
    */
    from("timer:kickstartmyroutes?delay=1000&repeatCount=1")
      .choice()
        .when().constant(config.batchIngest().enabled())
          .to("direct:kickstartmyingest")
        .otherwise()
          .to(("direct:kickstartmypoller"))
      .end()
    ;
    
    /*
     * Determine which batch ingest route should start.
    */
    from("direct:kickstartmyingest")
      .filter().constant(!config.batchIngest().enabled())
        .stop()
      .end()
      .choice()
        .when().constant(config.batchIngest().type() == EMBEDDED)
          .to("controlbus:route?routeId=embeddedIngest&action=start&async=true")
        .when().constant(config.batchIngest().type() == FILE)
          .to("controlbus:route?routeId=fileIngest&action=start&async=true")
        .when().constant(config.batchIngest().type() == S3)
          .to("controlbus:route?routeId=s3Ingest&action=start&async=true")
      .end()
    ;
    
    /*
     * Start the poller route if polling is enabled.
    */
    from("direct:kickstartmypoller")
      .filter().constant(!config.poller().enabled())
        .stop()
      .end()
      .to("controlbus:route?routeId=poller&action=start&async=true")
    ;
    
    /*
     * Ensures a single worker at a time.
    */
    from("seda:singletonWorker?blockWhenFull=true&size=1&purgeWhenStopping=true")
      .routingSlip().header(ApplicationHeaders.SINGLETON_WORKER_URI)
    ;
    
    /*
     * Batch ingest from the embedded JSON file.
    */
    from("timer:embeddedIngest?repeatCount=1").routeId("embeddedIngest").autoStartup(false)
      .setHeader(ApplicationHeaders.SINGLETON_WORKER_URI).constant("direct:embeddedIngest")
      .to("seda:singletonWorker")
    ;
    from("direct:embeddedIngest")
      .log(LoggingLevel.INFO, log, "Loading embedded: key='classpath:data/company-overview.json'")
      .to("language:constant:resource:classpath:data/company-overview.json")
      .unmarshal().json(JsonLibrary.Jackson, List.class)
      .split().body()
        .setHeader(ApplicationHeaders.STOCK_SYMBOL).simple("${body['Symbol']}")
        .to("direct:upsertCompanyOverviewToWeaviate")
      .end()
      .to("direct:kickstartmypoller")
    ;
    
    /*
     * Batch ingest from local JSON file. Optionally watch the file for changes.
    */
    fromF("file:%s?fileName=%s&delay=%s&repeatCount=%s&noop=true&idempotent=false",
           config.file().directory(),
           config.file().fileName(),
           config.file().watchPeriod(),
           (config.file().watch())?0:1
         ).routeId("fileIngest").autoStartup(false)
      .setHeader(ApplicationHeaders.SINGLETON_WORKER_URI).constant("direct:fileIngest")
      .to("seda:singletonWorker")
    ;
    from("direct:fileIngest")
      .log(LoggingLevel.DEBUG, log, String.format("Picked up file: name='${header.%s}'", Exchange.FILE_NAME))
      .unmarshal().json(JsonLibrary.Jackson, List.class)
      .enrich().constant("direct:calculateDeterministicHash").aggregationStrategy("deterministicHashHeaderEnrichmentStrategy").end()
      .log(LoggingLevel.DEBUG, log, String.format("Checking file should process: name='${header.%s}', deterministicHash='${header.%s}'", Exchange.FILE_NAME, ApplicationHeaders.DETERMINISTIC_HASH))
      .idempotentConsumer().header(ApplicationHeaders.DETERMINISTIC_HASH).idempotentRepository("batchIngestHashIdempotentRepository")
        .log(LoggingLevel.INFO, log, String.format("Processing file: name='${header.%s}', deterministicHash='${header.%s}'", Exchange.FILE_NAME, ApplicationHeaders.DETERMINISTIC_HASH))
        .split().body()
          .setHeader(ApplicationHeaders.STOCK_SYMBOL).simple("${body['Symbol']}")
          .multicast()
            .to("direct:upsertCompanyOverviewToWeaviate")
            .to("direct:updateBatchIngestFile")
          .end()
        .end()
      .end()
      .to("direct:kickstartmypoller")
    ;
    
    /*
     * Update the local JSON file if modifications have been made.
    */
    from("direct:updateFileBatchIngestFile")
      .aggregate().constant(true).aggregationStrategy("companyOverviewAggregationStrategy").eagerCheckCompletion().completionPredicate(header(Exchange.SPLIT_COMPLETE))
        .setBody().simple("${body.values()}")
        .setHeader(Exchange.FILE_NAME).constant(config.file().fileName())
        .enrich().constant("direct:calculateDeterministicHash").aggregationStrategy("deterministicHashHeaderEnrichmentStrategy").end()
        .log(LoggingLevel.DEBUG, log, String.format("Checking file should update: name='${header.%s}', deterministicHash='${header.%s}'", Exchange.FILE_NAME, ApplicationHeaders.DETERMINISTIC_HASH))
        .idempotentConsumer().header(ApplicationHeaders.DETERMINISTIC_HASH).idempotentRepository("batchIngestHashIdempotentRepository")
          .log(LoggingLevel.INFO, log, String.format("Updating file: name='${header.%s}', deterministicHash='${header.%s}'", Exchange.FILE_NAME, ApplicationHeaders.DETERMINISTIC_HASH))
          .marshal().json(JsonLibrary.Jackson, true)
          .toF("file:%s", config.file().directory())
        .end()
      .end()
    ;
    
    /*
     * Batch ingest from the AWS S3 JSON file. Optionally watch the file for changes.
    */
    fromF("aws2-s3:%s?region=%s&fileName=RAW(%s)&accessKey=RAW(%s)&secretKey=RAW(%s)&repeatCount=%s&delay=%s&deleteAfterRead=false",
            config.s3().bucketName(), 
            config.s3().regionName(),
            config.s3().fileName(),
            config.s3().accessKey(),
            config.s3().secretKey(),
            (config.s3().watch())?0:1,
            config.s3().watchPeriod()
         ).routeId("s3Ingest").autoStartup(false)
      .setHeader(ApplicationHeaders.SINGLETON_WORKER_URI).constant("direct:s3Ingest")
      .to("seda:singletonWorker")
    ;
    from("direct:s3Ingest")
      .log(LoggingLevel.DEBUG, log, String.format("Checking S3 should download: key='${header.%s}', s3hash='${header.%s}'", AWS2S3Constants.KEY, AWS2S3Constants.E_TAG))
      .idempotentConsumer().header(AWS2S3Constants.E_TAG).idempotentRepository("batchIngestHashIdempotentRepository")
        .log(LoggingLevel.DEBUG, log, String.format("Downloading S3: key='${header.%s}', s3hash='${header.%s}'", AWS2S3Constants.KEY, AWS2S3Constants.E_TAG))
        .unmarshal().json(JsonLibrary.Jackson, List.class)
        .enrich().constant("direct:calculateDeterministicHash").aggregationStrategy("deterministicHashHeaderEnrichmentStrategy").end()
        .log(LoggingLevel.DEBUG, log, String.format("Checking S3 should process: key='${header.%s}', deterministicHash='${header.%s}'", AWS2S3Constants.KEY, ApplicationHeaders.DETERMINISTIC_HASH))
        .idempotentConsumer().header(ApplicationHeaders.DETERMINISTIC_HASH).idempotentRepository("batchIngestHashIdempotentRepository")
          .log(LoggingLevel.INFO, log, String.format("Processing S3: key='${header.%s}', deterministicHash='${header.%s}'", AWS2S3Constants.KEY, ApplicationHeaders.DETERMINISTIC_HASH))
          .split().body()
            .setHeader(ApplicationHeaders.STOCK_SYMBOL).simple("${body['Symbol']}")
            .multicast()
              .to("direct:upsertCompanyOverviewToWeaviate")
              .to("direct:updateBatchIngestFile")
            .end()
          .end()
        .end()
        .to("direct:kickstartmypoller")
      .end()
    ;
    
    /*
     * Update the AWS S3 JSON file if modifications have been made.
    */
    from("direct:updateS3BatchIngestFile")
      .aggregate().constant(true).aggregationStrategy("companyOverviewAggregationStrategy").eagerCheckCompletion().completionPredicate(header(Exchange.SPLIT_COMPLETE))
        .setBody().simple("${body.values()}")
        .setHeader(AWS2S3Constants.KEY).constant(config.s3().fileName())
        .enrich().constant("direct:calculateDeterministicHash").aggregationStrategy("deterministicHashHeaderEnrichmentStrategy").end()
        .log(LoggingLevel.DEBUG, log, String.format("Checking S3 should update: key='${header.%s}', deterministicHash='${header.%s}'", AWS2S3Constants.KEY, ApplicationHeaders.DETERMINISTIC_HASH))
        .idempotentConsumer().header(ApplicationHeaders.DETERMINISTIC_HASH).idempotentRepository("batchIngestHashIdempotentRepository")
          .log(LoggingLevel.INFO, log, String.format("Updating S3: key='${header.%s}', deterministicHash='${header.%s}'", AWS2S3Constants.KEY, ApplicationHeaders.DETERMINISTIC_HASH))
          .marshal().json(JsonLibrary.Jackson, true)
          .toF("aws2-s3:%s?region=%s&fileName=RAW(%s)&accessKey=RAW(%s)&secretKey=RAW(%s)",
            config.s3().bucketName(), 
            config.s3().regionName(),
            config.s3().fileName(),
            config.s3().accessKey(),
            config.s3().secretKey()
          )
          .idempotentConsumer().header(AWS2S3Constants.E_TAG).idempotentRepository("batchIngestHashIdempotentRepository")
            .log(LoggingLevel.INFO, log, String.format("Adding updated S3 hash: key='${header.%s}', s3hash='${header.%s}'", AWS2S3Constants.KEY, AWS2S3Constants.E_TAG))
          .end()
        .end()
      .end()
    ;
    
    /*
     * Update the batch ingest JSON file.
    */
    from("direct:updateBatchIngestFile")
      .choice()
        .when().constant(config.batchIngest().type() == FILE && config.file().update())
          .to("direct:updateFileBatchIngestFile")
        .when().constant(config.batchIngest().type() == S3 && config.s3().update())
          .to("direct:updateS3BatchIngestFile")
      .end()
    ;
    
    /*
     * Get the deterministic hash for the file. Sorts the JSON data before calculating so that it's consistent.
    */
    from("direct:calculateDeterministicHash")
      .marshal(
        dataFormat()
          .json()
          .library(JsonLibrary.Jackson)
          .enableFeatures(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS.name())
          .prettyPrint(false)
        .end()
      )
      .setBody().method(DigestUtils.class, "md5Hex(${body})")
    ;
    
    /*
     * Load the list of stock symbols and check each for updated company information.
    */
    fromF("timer:poller?delay=1000&period=%s", config.poller().period()).routeId("poller").autoStartup(false)
      .to("seda:poller")
    ;
    from("seda:poller?discardWhenFull=true&size=1")
      .setHeader(ApplicationHeaders.SINGLETON_WORKER_URI).constant("direct:poller")
      .to("seda:singletonWorker")
    ;
    from("direct:poller")
      .split().constant(config.poller().symbols())
        .log(LoggingLevel.INFO, log, "Fetching company overview: symbol='${body}'")
        .setHeader(ApplicationHeaders.STOCK_SYMBOL).body()
        .to("direct:fetchCompanyOverview")
        .filter(body().isNotNull())
          .multicast()
            .to("direct:upsertCompanyOverviewToWeaviate")
            .to("direct:updateBatchIngestFile")
          .end()
        .end()
      .end()
    ;
    
    /*
     * Invoke the Alpha Vantage API (throttled).
    */
    from("direct:fetchCompanyOverview")
      .throttle(config.alphaVantage().throttleRequests()).timePeriodMillis(config.alphaVantage().throttleRequests())
      .setHeader(Exchange.HTTP_QUERY, simple(String.format("function=%s&symbol=${header.%s}&apikey=%s", config.alphaVantage().function().toUpperCase(), ApplicationHeaders.STOCK_SYMBOL, config.alphaVantage().apiKey())))
      .toF("%s://%s:%s/%s?followRedirects=true", config.alphaVantage().scheme(), config.alphaVantage().host(), config.alphaVantage().port(), config.alphaVantage().path())
      .unmarshal().json(JsonLibrary.Jackson, Map.class)
      .filter().simple("${body} == ${null} || ${body.isEmpty()}")
        .log(LoggingLevel.INFO, log, String.format("Unable to fetch company overview: symbol='${header.%s}', message='Empty/null response returned from Alpha Advantage API.'", ApplicationHeaders.STOCK_SYMBOL))
        .stop()
      .end()
      .filter().simple("${body.containsKey('Error Message')}")
        .log(LoggingLevel.INFO, log, String.format("Unable to fetch company overview: symbol='${header.%s}', message='${body['Error Message']}'", ApplicationHeaders.STOCK_SYMBOL))
        .stop()
      .end()
    ;
    
    /*
     * Insert or update a company overview into Weaviate.
    */
    from("direct:upsertCompanyOverviewToWeaviate")
      .throttle(config.weaviate().throttleRequests()).timePeriodMillis(config.weaviate().throttleRequests())
      .setHeader(ApplicationHeaders.WEAVIATE_ID).method("weaviateHelper", String.format("calculateDeterministicUUID(${headers.%s})", ApplicationHeaders.STOCK_SYMBOL))
      .transform().method("weaviateHelper", "convertToWeaviateProperties(${body})")
      .log(LoggingLevel.INFO, log, String.format("Upserting object to weaviate: symbol='${header.%s}', id='${headers.%s}'", ApplicationHeaders.STOCK_SYMBOL, ApplicationHeaders.WEAVIATE_ID))
      .process("upsertWeaviateObjectProcessor")
    ;
  }
}
