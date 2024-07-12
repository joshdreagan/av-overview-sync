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

import com.github.f4b6a3.uuid.UuidCreator;
import com.github.f4b6a3.uuid.enums.UuidNamespace;
import java.util.Map;
import java.util.HashMap;
import org.springframework.stereotype.Component;

@Component
public class WeaviateHelper {

  public String calculateDeterministicUUID(String identifier) {
    return UuidCreator.getNameBasedSha1(UuidNamespace.NAMESPACE_DNS, identifier).toString();
  }
  
  public Map<String, Object> convertToWeaviateProperties(Map<String, Object> from) {
    Map<String, Object> to = new HashMap<>();
    from.forEach((k, v) -> {
      String modifiedK = switch (k) {
        case "52WeekHigh" -> "fiftytwoWeekHigh";
        case "52WeekLow" -> "fiftytwoWeekLow";
        case "50DayMovingAverage" -> "fiftyDayMovingAverage";
        case "200DayMovingAverage" -> "twohundredDayMovingAverage";
        default -> k.replaceFirst("^.", k.substring(0, 1).toLowerCase());
      };
      to.put(modifiedK, v);
    });
    return to;
  }
}
