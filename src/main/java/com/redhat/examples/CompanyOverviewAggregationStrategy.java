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

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.springframework.stereotype.Component;

@Component
public class CompanyOverviewAggregationStrategy implements AggregationStrategy {

  private final SortedMap<String, Object> companyOverviewMap = new TreeMap<>();

  public SortedMap<String, Object> getCompanyOverviewMap() {
    return companyOverviewMap;
  }

  @Override
  public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
    String symbol = newExchange.getIn().getHeader(ApplicationHeaders.STOCK_SYMBOL, String.class);
    Map json = newExchange.getIn().getBody(Map.class);
    companyOverviewMap.put(symbol, json);
    if (oldExchange == null) {
      newExchange.getIn().setBody(companyOverviewMap);
      return newExchange;
    }
    return oldExchange;
  }
}
