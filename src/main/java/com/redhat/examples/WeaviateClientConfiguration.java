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
import io.weaviate.client.Config;
import io.weaviate.client.WeaviateAuthClient;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.v1.auth.exception.AuthException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class WeaviateClientConfiguration {

  @Autowired
  ApplicationConfiguration config;

  @Bean
  WeaviateClient weaviateClient() throws AuthException {
    Config weaviateConfig = new Config(
      config.weaviate().scheme(),
      String.format("%s:%d", config.weaviate().host(), config.weaviate().port()),
      Map.of(
        "X-OpenAI-Api-key", config.weaviate().openAiApiKey(),
        "X-Huggingface-Api-key", config.weaviate().huggingfaceApiKey())
    );
    weaviateConfig.setGRPCSecured(config.weaviate().grpcSecured());
    weaviateConfig.setGRPCHost(String.format("%s:%d", config.weaviate().grpcHost(), config.weaviate().grpcPort()));
    return WeaviateAuthClient.apiKey(weaviateConfig, config.weaviate().apiKey());
  }
}
