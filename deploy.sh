#!/bin/bash

# deploy Strimzi
oc apply -f https://github.com/strimzi/strimzi-kafka-operator/releases/download/0.16.2/strimzi-cluster-operator-0.16.2.yaml

oc adm policy add-cluster-role-to-user cluster-admin system:serviceaccount:default:strimzi-cluster-operator

oc apply -f deployment/ccfd-kafka.yaml

oc expose svc/ccfd-kafka

oc wait kafka/ccfd --for=condition=Ready --timeout=300s

oc new-app python:3.6~https://github.com/ruivieira/ccfd-kafka-producer \
	-e BROKER_URL=ccfd-kafka-brokers:9092 \
	-e KAFKA_TOPIC=ccd

oc new-app ruivieira/jbpm-seldon-test-model

oc expose svc/jbpm-seldon-test-model

oc new-app ruivieira/ccfd-demo \
    -e BROKER_URL=ccfd-kafka-brokers:9092 \
    -e SELDON_URL=http://jbpm-seldon-test-model-default.apps-crc.testing

oc expose svc/ccfd-demo