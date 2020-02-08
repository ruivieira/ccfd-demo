#!/bin/bash

mvn -f ccd-model/pom.xml clean install
mvn -f ccd-kjar/pom.xml clean install -P openshift
mvn -f ccd-service/pom.xml clean install -P openshift,h2