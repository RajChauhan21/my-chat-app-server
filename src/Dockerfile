FROM maven:3.8.4-openjdk-17

WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src

RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

EXPOSE 8080

#  your JAR filename
CMD ["java", "-jar", "target/chat-0.0.1-SNAPSHOT.jar"]