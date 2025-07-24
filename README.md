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
./gradlew jmeterTestSuite -Phost=localhost -Pport=8080 -Pthreads=50 -Pduration=600
```

### Deploy to kubernetes
Edit the Redis host and port in the config map, and password in the secret via `kubectl` once deployed.
```sh
kubectl apply -f session-api-namespace.yaml
```
```sh
kubectl apply -f spring-api-configmap.yaml
```
```sh
kubectl apply -f spring-api-secret.yaml
```
```sh
kubectl apply -f session-api-deployment.yaml
```
To deploy the test runner:
```sh
kubectl apply -f runner-namespace.yaml
```
```sh
kubectl apply -f runner-deployment.yaml
```
```sh
kubectl exec -it java-runner-76f567d67c-djk87 -n java-runner -- /bin/bash
```
