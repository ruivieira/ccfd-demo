## Setup

### Requirements

You will need:

* Kafka
  * with a topic named `ccd`
  * messages with the format `{"id" : int, "amount" : double}`
* Seldon
  * Returning an `ndarray` with label probabilities for fraud/not-fraund (`[[0.12, 0.88]]`)

### Running locally

Set the environment variables pointing to the Kafka broker and Seldon's server:

* `BROKER_URL` - Kafka's broker adress
* `SELDON_URL`  - Seldon's server address

To build the dependencies and start the KIE server run:

```shell
$ cd ccd-service
$ ./launch.sh clean install
```

### Running on OpenShift

#### Kafka

Strimzi is used to provide Kafka on OpenShift. Start by applying the operator and the cluster deployment with:

```shell
$ oc apply -f https://github.com/strimzi/strimzi-kafka-operator/releases/download/0.16.2/strimzi-cluster-operator-0.16.2.yaml
$ oc apply -f deployment/ccfd-kafka.yaml
$ oc wait kafka/ccfd for=condition=Ready --timeout=300s
```

#### Kafka producer

To start the Kafka producer (which simulates the transaction events) run:

```shell
$ oc new-app python:3.6~https://github.com/ruivieira/ccfd-kafka-producer \
    -e BROKER_URL=<BROKER_URL> \
    -e KAFKA_TOPIC=<TOPIC>
```

#### Kie server

An [image is available](https://hub.docker.com/r/ruivieira/ccfd-demo), ready for deployment. Simply run:

```shell
$ oc new-app ruivieira/ccfd-demo \
    -e BROKER_URL=<BROKER_URL> \
    -e SELDON_URL=<SELDON_URL>
```



### Environment variables

* `BROKER_URL` - Kafka's broker adress
* `SELDON_URL`  - Seldon's server address