FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
ARG DEPENDENCY=target/dependency
COPY ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY ${DEPENDENCY}/BOOT-INF/classes /app
COPY ${DEPENDENCY}/META-INF /app/META-INF
ENTRYPOINT ["java","-cp","app:app/lib/*","uk.ac.ebi.protvar.ApplicationMainClass"]