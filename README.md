# redis-session-api-test

### Build docker image
```sh
./gradlew buildDockerImage
```

### Start services
```sh
./gradlew composeUp
```

### Stop services
```sh
./gradlew composeDown
```

### Run standalone container
```sh
./gradlew runContainer -PredisHost=localhost -PredisPort=6379 -PredisPassword=password
```

### Push docker image
```sh
./gradlew pushImage
```

### Run test
```sh
./gradlew jmeterTestSuite -Phost=localhost -Pport=8080
```

### Run test with additional parameters
```sh
./gradlew jmeterTestSuite -Phost=localhost -Pport=8080 -Pthreads=50 -PrampUp=120 -Pduration=600
```

### Deploy to kubernetes
Set `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, and `DOCKER_REGISTRY` environment variables.
The `REDIS_PASSWORD` environment variable should be base64 encoded.
```sh
envsubst < session-api.yaml | kubectl apply -f -
```

```sh
envsubst < runner.yaml | kubectl apply -f -
```
