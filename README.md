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

### Deploy to kubernetes
```sh
helm repo add redis-session-api-test https://mminichino.github.io/redis-session-api-test
```
```sh
helm repo update
```
```sh
helm install session-api redis-session-api-test/session-api -n session-api --create-namespace --set redisHost=redb.usc1-demo.svc.cluster.local --set redisPassword=password  --set metrics=true
```
```sh
kubectl exec -it java-runner-76f567d67c-djk87 -n java-runner -- /bin/bash
```

### Run test
```sh
./gradlew jmeterTestSuite -Phost=session-api-service.session-api.svc.cluster.local -Pthreads=32
```

### Run test with additional parameters
```sh
./gradlew jmeterTestSuite -Phost=session-api-service.session-api.svc.cluster.local -Pthreads=64 -Pduration=600
```

### Run the test external to kubernetes (via ingress, load balancer or route)
```shell
./gradlew jmeterTestSuite -Phost=session-api.apps.demo.example.com -Pport=80
```
