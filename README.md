# Alpha Vantage - Company Info - Sync

This application can be used to synchronize the company information from the Alpha Vantage API into a Weaviate VectorDB.

## Prerequisites

- An Alpha Vantage API key
  - You can use the default API key "demo", but it will only give you access to the company information for the "IBM" stock symbol.
- A Weaviate API key
  - If running a local Weaviate instance, you can use an empty "" API key.
- An OpenAI API key
- A Huggingface API key

## Running locally

If you'd like, you can run a Weaviate instance locally using podman/docker

```
podman run --name weaviate -p 8000:8080 -p 50051:50051 -e ENABLE_MODULES="text2vec-cohere,text2vec-huggingface,text2vec-palm,text2vec-openai,generative-openai,generative-cohere,generative-palm,ref2vec-centroid,reranker-cohere,qna-openai" semitechnologies/weaviate:1.25.7
```

You can override any of the application configurations by creating an `application.yml` file in the `$PROJECT_ROOT/config` directory. This directory is ignored by Git to prevent checking in personal/user keys or other secrets.

To run the application, you can use the following commands:

```
cd $PROJECT_ROOT
mvn clean package spring-boot:run
```

## Running on OpenShift

Create the `deployment.yml, `configmap.yml`, and `secret.yml` files from the available templates, and update them with your values. These files are ignored by Git to prevent checking in personal/user keys or other secrets.

```
cd $PROJECT_ROOT
cp src/main/jkube/deployment.yml.template src/main/jkube/deployment.yml
cp src/main/jkube/configmap.yml.template src/main/jkube/configmap.yml
cp src/main/jkube/secret.yml.template src/main/jkube/secret.yml
```

Build and deploy to OpenShift. _Make sure you're logged in to OpenShift and are currently in the namespace you want to deploy to._

```
cd $PROJECT_ROOT
mvn -P openshift clean package oc:deploy
```

Additional notes:

You can reference environment variables inside of your ConfigMap. For instance, if you wanted to use the Huggingface or OpenAI vectorizers, you could add the following configurations to your `config.yml` file:

```
data:
  application.yml: |-
    application:
      weaviate:
        headers:
          "X-OpenAI-Api-key": "${OPENAI_API_KEY}"
          "X-Huggingface-Api-key": "${HUGGINGFACE_API_KEY}"
        schema:
          vectorizer-module: "text2vec-huggingface"
          vectorizer-module-config:
            options:
              waitForModel: true
          generative-module: "generative-openai"
          generative-module-config:
            model: "gpt-4"
```

Then you can add the API keys to your `secret.yml` file like so:

```
data:
  application.weaviate.openai-api-key: "<my base64 encoded key>"
  application.weaviate.huggingface-api-key: "<my base64 encoded key>"
```

Finally, you'd inject those secrets in your `deployment.yml` as environment variables which you referenced in your ConfigMap.

```
spec:
  template:
    spec:
      containers:
        - name: OPENAI_API_KEY
          valueFrom:
            secretKeyRef:
              name: ${project.artifactId}-secret
              key: application.weaviate.openai-api-key
              optional: true
        - name: HUGGINGFACE_API_KEY
          valueFrom:
            secretKeyRef:
              name: ${project.artifactId}-secret
              key: application.weaviate.huggingface-api-key
              optional: true
```

## Application Properties

| Property | Default | Description |
| :------- | :------ | :---------- |
| `application.batch-ingest.enabled` | `true` | Enable batch ingest of company information. When enabled, this will run first before the poller is started.
| `application.batch-ingest.type` | `EMBEDDED` | The batch ingest type. Valid values are `EMBEDDED`, `FILE`, or `S3`. If using the `FILE` type, set the appropriate settings starting with `application.file`. If using the `S3` type, set the appropriate settings starting with `application.s3`.
| `application.file.directory` | "target/batch" | The directory where the batch file is stored.
| `application.file.file-name` | "company-overview.json" | The file name of the batch file.
| `application.file.watch` | `false` | Watch the specified batch file for changes.
| `application.file.watch-period` | `30000` | The frequency (in milliseconds) to check for changes to the specified batch file. Only applies if `watch` is `true`.
| `application.file.update` | `false` | Update the specified batch file if new data is found during polling.
| `application.s3.access-key` |  | Your AWS access key.
| `application.s3.secret-key` |  | Your AWS secret key.
| `application.s3.bucket-name` | "default" | The AWS S3 bucket name where the batch file resides.
| `application.s3.region-name` | "us-east-1" | The AWS S3 region where your batch file resides.
| `application.s3.file-name` | "company-overview.json" | The file name (AWS S3 Key) of the batch file/S3 object.
| `application.s3.watch` | `false` | Watch the specified batch file/S3 object for changes.
| `application.s3.watch-period` | `30000` | The frequency (in milliseconds) to check for changes to the specified batch file/S3 object. Only applies if `watch` is `true`.
| `application.s3.update` | `false` | Update the specified batch file/S3 object if new data is found during polling.
| `application.poller.enabled` | `true` | Enable polling the Alpha Vantage API for updates to company information.
| `application.poller.symbols` | "IBM" | The list of stock symbols to update on poll.
| `application.poller.period` | `86400000` | The frequency (in milliseconds) to poll.
| `application.alpha-vantage.scheme` | "https" | The scheme for the Alpha Vantage API. Valid values are "http" or "https".
| `application.alpha-vantage.host` | "www.alphavantage.co" | The host name for the Alpha Vantage API.
| `application.alpha-vantage.port` | `443` | The port for the Alpha Vantage API.
| `application.alpha-vantage.path` | "query" | The path for the Alpha Vantage API.
| `application.alpha-vantage.function` | "OVERVIEW" | The function for the Alpha Vantage API. Supported values are "OVERVIEW".
| `application.alpha-vantage.api-key` | "demo" | The API key for the Alpha Vantage API. The default "demo" key only gives access to the "IBM" stock symbol.
| `application.alpha-vantage.throttle-enabled` | `true` | Should requests to the Alpha Vantage API be throttled.
| `application.alpha-vantage.throttle-requests` | `1` | The number of requests per-period allowed to the Alpha Vantage API.
| `application.alpha-vantage.throttle-period` | `1000` | The period (in milliseconds) for which to throttle requests to the Alpha Vantage API.
| `application.weaviate.scheme` | "http" | The scheme for the Weaviate VectorDB. Valid values are "http" or "https".
| `application.weaviate.host` | "localhost" | The host name for the Weaviate VectorDB.
| `application.weaviate.port` | `8000` | The port for the Weaviate VectorDB.
| `application.weaviate.grpc-secured` | `false` | Enable secure gRPC access to the Weaviate VectorDB.
| `application.weaviate.grpc-host` | "localhost" | The host name for gRPC access to the Weaviate VectorDB.
| `application.weaviate.grpc-port` | `50051` | The port for gRPC access to the Weaviate VectorDB.
| `application.weaviate.api-key` |  | Your Weavate API key to use for access to the Weaviate VectorDB.
| `application.weaviate.headers` |  | The headers to include in the connection to the Weaviate VectorDB. For example, "X-OpenAI-Api-key" or "X-Huggingface-Api-key".
| `application.weaviate.throttle-enabled` | `true` | Should requests to the Weaviate VectorDB be throttled.
| `application.weaviate.throttle-requests` | `1` | The number of requests per-period allowed to the Weaviate VectorDB.
| `application.weaviate.throttle-period` | `1000` | The period (in milliseconds) for which to throttle requests to the Weaviate VectorDB.
| `application.weaviate.schema.initialize` | `true` | Should this app initialize the schema in the Weaviate VectorDB on startup.
| `application.weaviate.schema.drop-if-exists` | `false` | Should this app drop the existing schema and recreate in the Weaviate VectorDB on startup.
| `application.weaviate.schema.name` | "Symbols" | The class name of the schema in the Weaviate VectorDB on startup.
| `application.weaviate.schema.vectorizer-module` | "text2vec-ollama" | The name of the vectorizer module to use when creating the schema in the Weaviate VectorDB. For instance "text2vec-ollama" or "text2vec-huggingface".
| `application.weaviate.schema.vectorizer-module-config` | `apiEndpoint: "http://localhost:11434", model: "all-minilm"` | The vectorizer module configuration map to use when creating the schema in the Weaviate VectorDB.
| `application.weaviate.schema.generative-module` | "generative-ollama" | The name of the generative module to use when creating the schema in the Weaviate VectorDB. For instance "generative-ollama" or "generative-openai".
| `application.weaviate.schema.generative-module-config` | `apiEndpoint: "http://localhost:11434", model: "llama3"` | The generative module configuration map to use when creating the schema in the Weaviate VectorDB.



