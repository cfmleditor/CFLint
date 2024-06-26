FROM gradle:5.6.2-jdk8 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build -x test --no-daemon

FROM openjdk:8-jre-slim

RUN mkdir /app

COPY --from=build /home/gradle/src/build/libs/CFLint*-all.jar /app/CFLint.jar

ENTRYPOINT ["java", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-Djava.security.egd=file:/dev/./urandom","-jar","/app/CFLint.jar"]