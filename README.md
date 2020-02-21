# CCFD demo

## Description

![diagram](docs/diagram.png)

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
* `KAFKA_TOPIC` - topic which Camel listen too
* `SELDON_TOKEN` (optional) - Seldon's authentication token

To build the dependencies and start the KIE server run:

```shell
$ cd ccd-service
$ ./launch.sh clean install
```

### Running on OpenShift

To deploy all the components in OpenShift, the simplest way is to login using `oc`, e.g.:

```shell
$ oc login -u <USER>
```

Next you can create a project for this demo, such as

```shell
$ oc new-project ccfd
```

#### Kafka

Strimzi is used to provide Apache Kafka on OpenShift. Start by applying the operator [0] and the cluster deployment with:

```shell
$ oc apply -f https://github.com/strimzi/strimzi-kafka-operator/releases/download/0.16.2/strimzi-cluster-operator-0.16.2.yaml
$ oc apply -f deployment/ccfd-kafka.yaml
$ oc expose svc/ccfd-kafka-brokers
$ oc wait kafka/ccfd --for=condition=Ready --timeout=300s
```

#### Kafka producer

To start the Kafka producer (which simulates the transaction events) run:

```shell
$ oc new-app python:3.6~https://github.com/ruivieira/ccfd-kafka-producer \
    -e BROKER_URL=<BROKER_URL> \
    -e KAFKA_TOPIC=<TOPIC>
```

The Kafka producer creates a message stream with data from a sample of the [Kaggle credit card fraud dataset](https://www.kaggle.com/mlg-ulb/creditcardfraud).

#### Seldon

To deploy the Seldon model server, deploy the already built Docker image with

```shell
$ oc new-app ruivieira/ccfd-seldon-model
$ oc expose svc/ccfd-seldon-model
```

#### Kie server

To deploy the KIE server (this repo), Maven and a JDK need to be available on your local machine. We use `fabric8` to build the images as:

```shell
$ mvn -f ccd-model/pom.xml clean install
$ mvn -f ccd-kjar/pom.xml clean install -P openshift
$ mvn -f ccd-service/pom.xml clean install -P openshift,h2
```

Or simply run `build.sh`. Once the image is built we create it on OpenShift with:

```shell
$ oc new-app ccd-service:1.0-SNAPSHOT \
    -e SELDON_URL=<SELDON_URL>
$ oc expose svc/ccfd-demo
```

If the Seldon server requires an authentication token, this can be passed to the KIE server by adding the following environment variable:

```shell
-e SELDON_TOKEN=<SELDON_TOKEN>
```

#### Camel router

The Camel router is responsible consume messages arriving in specific topics, requesting a prediction to the Seldon model, and then triggering different REST endpoints according to that prediction.
The route is selected depending on whether a transaction is predicted as fraudulent or not. Depending on the model's prediction a specific business process will be triggered on the KIE server.
To deploy a router with listens to the topic `KAFKA_TOPIC` from Kafka's broker `BROKER_URL` and starts a process instance on the KIE server at `KIE_SERVER_URL`, first build the [router located here](https://github.com/ruivieira/ccfd-fuse) with

```shell
$ git clone https://github.com/ruivieira/ccfd-fuse.git
$ mvn -f ccfd-fuse/pom.xml clean install -P openshift
```

and then deploy it with

```shell
$ oc new-app ccd-fuse:1.0-SNAPSHOT \
    -e BROKER_URL=ccfd-kafka-brokers:9092 \
    -e KAFKA_TOPIC=ccd \
    -e KIE_SERVER_URL=ccd-service:8090
    -e SELDON_URL=<SELDON_URL>
```

Also optionally, a Seldon token can be provided:

```shell
-e SELDON_TOKEN=<SELDON_TOKEN>
```

### Environment variables

* `BROKER_URL` - Kafka's broker adress
* `SELDON_URL`  - Seldon's server address
* `KAFKA_TOPIC` - topic which Camel listen too
* `SELDON_TOKEN` (optional) - Seldon's authentication token

## Footnotes

[0] - In case you need cluster admin privileges to deploy Strimzi, in which case (in a development setup)  you can run `oc adm policy add-cluster-role-to-user cluster-admin system:serviceaccount:default:strimzi-cluster-operator`.