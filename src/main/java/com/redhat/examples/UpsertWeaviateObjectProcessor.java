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

import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import io.weaviate.client.v1.data.model.WeaviateObject;
import java.util.List;
import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UpsertWeaviateObjectProcessor implements Processor {

  private static final Logger log = LoggerFactory.getLogger(UpsertWeaviateObjectProcessor.class);

  @Autowired
  ApplicationConfiguration config;
  
  @Autowired
  WeaviateClient weaviateClient;

  @Override
  public void process(Exchange exchange) throws Exception {
    String id = exchange.getIn().getHeader(ApplicationHeaders.WEAVIATE_ID, String.class);
    Map<String, Object> properties = exchange.getIn().getBody(Map.class);

    log.debug("Querying objects: id='{}'", id);
    Result<List<WeaviateObject>> getObjectResult = weaviateClient.data().objectsGetter().withClassName(config.weaviate().schema().name()).withID(id).run();
    if (getObjectResult.hasErrors()) {
      throw new RuntimeException(getObjectResult.getError().toString());
    }
    log.debug("Finished querying objects: id='{}', objectCount='{}'", id, (getObjectResult.getResult() != null) ? getObjectResult.getResult().size() : 0);

    if (getObjectResult.getResult() == null || getObjectResult.getResult().size() == 0) {
      log.debug("Creating object: id='{}', properties='{}'", id, properties);
      Result<WeaviateObject> insertObjectResult = weaviateClient.data().creator().withClassName(config.weaviate().schema().name()).withID(id).withProperties(properties).run();
      if (insertObjectResult.hasErrors()) {
        throw new RuntimeException(insertObjectResult.getError().toString());
      }
      log.debug("Finished creating object: id='{}'", id);
    } else if (getObjectResult.getResult().size() == 1) {
      if (!properties.equals(getObjectResult.getResult().get(0).getProperties())) {
        log.debug("Updating object: id='{}', properties='{}'", id, properties);
        Result<Boolean> updateObjectResult = weaviateClient.data().updater().withClassName(config.weaviate().schema().name()).withID(id).withProperties(properties).run();
        if (updateObjectResult.hasErrors()) {
          throw new RuntimeException(updateObjectResult.getError().toString());
        }
        log.debug("Finished updating object: id='{}'", id);
      } else {
        log.debug("Skipping update object: id='{}'", id);
      }
    } else {
      throw new RuntimeException(String.format("Multiple objects found: id='%s', objectCount='%d'", id, getObjectResult.getResult().size()));
    }
  }
}
