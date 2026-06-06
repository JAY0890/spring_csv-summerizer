FROM eclipse-temurin:25

WORKDIR /springapp

COPY ./target/csvsummerizer-0.0.1-SNAPSHOT.jar  .

EXPOSE 8080

CMD [ "java","-jar","csvsummerizer-0.0.1-SNAPSHOT.jar" ]