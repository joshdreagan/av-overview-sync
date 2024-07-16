/*
 * Licensed under the Apache License, Version 2.0 (the "License", List.of("text"));
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

import java.util.Map;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import io.weaviate.client.v1.schema.model.Property;
import io.weaviate.client.v1.schema.model.WeaviateClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class WeaviateSchemaCreator {

  private static final Logger log = LoggerFactory.getLogger(WeaviateSchemaCreator.class);

  @Autowired
  ApplicationConfiguration config;
  
  @Autowired
  WeaviateClient weaviateClient;

  @EventListener(ApplicationReadyEvent.class)
  void initWeaviateSchema() {
    log.debug("Initializing schema: name='{}'", config.weaviate().schema().name());
    if (!config.weaviate().schema().initialize()) {
      log.debug("Skipping schema initialization: name='{}'", config.weaviate().schema().name());
      return;
    }
    
    Result<Boolean> classExistsResult = weaviateClient.schema().exists().withClassName(config.weaviate().schema().name()).run();
    if (classExistsResult.hasErrors()) {
      throw new RuntimeException(classExistsResult.getError().toString());
    }
    
    boolean shouldCreate = !classExistsResult.getResult() || config.weaviate().schema().dropIfExists();
    
    if (config.weaviate().schema().dropIfExists()) {
      log.debug("Dropping existing schema: name='{}'", config.weaviate().schema().name());
      Result<Boolean> schemaDeleteResult = weaviateClient.schema().classDeleter().withClassName(config.weaviate().schema().name()).run();
      if (schemaDeleteResult.hasErrors()) {
        throw new RuntimeException(schemaDeleteResult.getError().toString());
      }
    }

    if (shouldCreate) {
      log.debug("Creating schema: name='{}'", config.weaviate().schema().name());
      Result<Boolean> schemaCreateResult = weaviateClient.schema().classCreator()
        .withClass(
          WeaviateClass.builder()
            .className(config.weaviate().schema().name())
            .properties(
              config.weaviate().schema().properties().stream().map((t) -> {
                return Property.builder()
                  .name(t.name())
                  .dataType(t.dataTypes())
                  .build();
              }).toList()
            )
            .vectorizer(config.weaviate().schema().vectorizerModule())
            .moduleConfig(
              Map.of(
                config.weaviate().schema().vectorizerModule(), config.weaviate().schema().vectorizerModuleConfig(),
                config.weaviate().schema().generativeModule(), config.weaviate().schema().generativeModuleConfig()
              )
            )
            .build()
        )
        .run();
      if (schemaCreateResult.hasErrors()) {
        throw new RuntimeException(schemaCreateResult.getError().toString());
      }
    }
  }
}
