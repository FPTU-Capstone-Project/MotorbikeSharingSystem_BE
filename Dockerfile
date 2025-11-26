FROM maven:3.9.8-amazoncorretto-21 AS build

WORKDIR /app
COPY pom.xml .
COPY src ./src

# Build with options to handle compiler warnings and issues
RUN mvn clean package -DskipTests \
    -Dmaven.compiler.failOnWarning=false \
    -Dmaven.compiler.showWarnings=false \
    -Dmapstruct.unmappedTargetPolicy=IGNORE 2>&1 | tee /tmp/mvn.log ; \
    if [ ${PIPESTATUS[0]} -ne 0 ]; then \
        echo "=== COMPILATION ERRORS ===" ; \
        grep -B5 "actual and formal argument lists differ" /tmp/mvn.log || cat /tmp/mvn.log | grep -A5 "ERROR" ; \
        exit 1 ; \
    fi

FROM amazoncorretto:21

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# nho doi ip port cua db trong application properties thanh ip nhe