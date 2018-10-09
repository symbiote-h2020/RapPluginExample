FROM openjdk:8-jre-alpine

WORKDIR /home
ARG COMPONENT_BUILD_VERSION

ENV componentName "RapPluginExample"
ENV componentVersion ${COMPONENT_BUILD_VERSION}
ENV JVM_OPTS "-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:MaxRAMFraction=1"

COPY build/libs/$componentName-$componentVersion.jar /home/
COPY src/main/resources/bootstrap-docker.properties /home/bootstrap.properties

CMD java $JAVA_HTTP_PROXY $JAVA_HTTPS_PROXY $JAVA_NON_PROXY_HOSTS $JVM_OPTS -DSPRING_BOOT_WAIT_FOR_SERVICES=symbiote-rap:8103 -jar $componentName-${componentVersion}.jar
