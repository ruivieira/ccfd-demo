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

To deploy all the components in OpenShift, the simplest way is to login using `oc`, e.g.:

```shell
$ oc login -u <USER>
```

and running the `deploy.sh` script. Below is a list of the components deployed by this script.

#### Kafka

Strimzi is used to provide Apache Kafka on OpenShift. Start by applying the operator [0] and the cluster deployment with:

```shell
$ oc apply -f https://github.com/strimzi/strimzi-kafka-operator/releases/download/0.16.2/strimzi-cluster-operator-0.16.2.yaml
$ oc apply -f deployment/ccfd-kafka.yaml
$ oc expose svc/ccfd-kafka
$ oc wait kafka/ccfd --for=condition=Ready --timeout=300s
```

#### Kafka producer

To start the Kafka producer (which simulates the transaction events) run:

```shell
$ oc new-app python:3.6~https://github.com/ruivieira/ccfd-kafka-producer \
    -e BROKER_URL=<BROKER_URL> \
    -e KAFKA_TOPIC=<TOPIC>
```

#### Seldon

To deply the Seldon model server, deploy the already built Docker image with

```shell
$ oc new-app ruivieira/jbpm-seldon-test-model
$ oc expose svc/jbpm-seldon-test-model
```

#### Kie server

To deploy the KIE server, Maven and a JDK need to be available on your local machine. We use `fabric8` to build the images as:

```shell
$ mvn -f ccd-model/pom.xml clean install
$ mvn -f ccd-kjar/pom.xml clean install -P openshift
$ mvn -f ccd-service/pom.xml clean install -P openshift,h2
```

One the image is built we create it on OpenShift with:

```shell
$ oc new-app ccfd-service:1.0-SNAPSHOT \
    -e BROKER_URL=<BROKER_URL> \
    -e SELDON_URL=<SELDON_URL>
$ oc expose svc/ccfd-demo
```

### Environment variables

* `BROKER_URL` - Kafka's broker adress
* `SELDON_URL`  - Seldon's server address



## Footnotes

[0] - In case you need cluster admin privileges to deploy Strimzi, in which case (in a development setup)  you can run `oc adm policy add-cluster-role-to-user cluster-admin system:serviceaccount:default:strimzi-cluster-operator`.