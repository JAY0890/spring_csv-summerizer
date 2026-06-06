FROM maven:3.9.16 AS builder

WORKDIR /mavenbuild

COPY . .

RUN mvn clean package -DskipTests
 

FROM eclipse-temurin:25

WORKDIR /springapp

COPY --from=builder /mavenbuild/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT [ "java","-jar","app.jar" ]