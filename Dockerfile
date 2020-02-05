FROM registry.access.redhat.com/ubi8/ubi-minimal:latest
RUN microdnf install java-1.8.0-openjdk-headless && microdnf install shadow-utils && microdnf clean all

USER root

WORKDIR /home/app/deployments

ADD ccd-kjar/target/ccd-kjar-1.0-SNAPSHOT.jar /home/app/deployments/
ADD ccd-model/target/ccd-model-1.0-SNAPSHOT.jar /home/app/deployments/
ADD ccd-service/target/ccd-service-1.0-SNAPSHOT.jar /home/app/deployments/

WORKDIR /home/app/deployments/
EXPOSE 8090
CMD ["java", "-jar", "ccd-service-1.0-SNAPSHOT.jar"]