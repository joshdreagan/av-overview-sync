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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class WeaviateSchemaCreator {

  private static final Logger log = LoggerFactory.getLogger(WeaviateSchemaCreator.class);

  public static final Map<String, List<String>> SCHEMA_PROPERTIES;

  static {
    Map<String, List<String>> properties = new HashMap<>();
    properties.put("twohundredDayMovingAverage", List.of("text"));
    properties.put("fiftyDayMovingAverage", List.of("text"));
    properties.put("fiftytwoWeekLow", List.of("text"));
    properties.put("fiftytwoWeekHigh", List.of("text"));
    properties.put("Address", List.of("text"));
    properties.put("analystRatingBuy", List.of("text"));
    properties.put("analystRatingHold", List.of("text"));
    properties.put("analystRatingSell", List.of("text"));
    properties.put("analystRatingStrongBuy", List.of("text"));
    properties.put("analystRatingStrongSell", List.of("text"));
    properties.put("analystTargetPrice", List.of("text"));
    properties.put("assetType", List.of("text"));
    properties.put("beta", List.of("text"));
    properties.put("bookValue", List.of("text"));
    properties.put("cIK", List.of("text"));
    properties.put("country", List.of("text"));
    properties.put("currency", List.of("text"));
    properties.put("description", List.of("text"));
    properties.put("dilutedEPSTTM", List.of("text"));
    properties.put("dividendDate", List.of("text"));
    properties.put("dividendPerShare", List.of("text"));
    properties.put("dividendYield", List.of("text"));
    properties.put("eBITDA", List.of("text"));
    properties.put("ePS", List.of("text"));
    properties.put("eVToEBITDA", List.of("text"));
    properties.put("eVToRevenue", List.of("text"));
    properties.put("exDividendDate", List.of("text"));
    properties.put("exchange", List.of("text"));
    properties.put("fiscalYearEnd", List.of("text"));
    properties.put("forwardPE", List.of("text"));
    properties.put("grossProfitTTM", List.of("text"));
    properties.put("industry", List.of("text"));
    properties.put("latestQuarter", List.of("text"));
    properties.put("marketCapitalization", List.of("text"));
    properties.put("name", List.of("text"));
    properties.put("operatingMarginTTM", List.of("text"));
    properties.put("pEGRatio", List.of("text"));
    properties.put("pERatio", List.of("text"));
    properties.put("priceToBookRatio", List.of("text"));
    properties.put("priceToSalesRatioTTM", List.of("text"));
    properties.put("profitMargin", List.of("text"));
    properties.put("quarterlyEarningsGrowthYOY", List.of("text"));
    properties.put("quarterlyRevenueGrowthYOY", List.of("text"));
    properties.put("returnOnAssetsTTM", List.of("text"));
    properties.put("returnOnEquityTTM", List.of("text"));
    properties.put("revenuePerShareTTM", List.of("text"));
    properties.put("revenueTTM", List.of("text"));
    properties.put("sector", List.of("text"));
    properties.put("sharesOutstanding", List.of("text"));
    properties.put("symbol", List.of("text"));
    properties.put("trailingPE", List.of("text"));
    SCHEMA_PROPERTIES = Collections.unmodifiableMap(properties);
  }

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
              SCHEMA_PROPERTIES.entrySet().stream().map((t) -> {
                return Property.builder()
                  .name(t.getKey())
                  .dataType(t.getValue())
                  .build();
              }).toList()
            )
            .vectorizer(config.weaviate().schema().vectorizer())
            .moduleConfig(config.weaviate().schema().moduleConfig())
            .build()
        )
        .run();
      if (schemaCreateResult.hasErrors()) {
        throw new RuntimeException(schemaCreateResult.getError().toString());
      }
    }
  }
}
