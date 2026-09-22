# ============================================================
#  NetPlus — image de l'API (Spring Boot 4 / Java 21)
#
#  Le jar est construit AVANT l'image, par le pipeline (mvnw verify) ou à
#  la main (./mvnw -DskipTests package) : le Dockerfile ne recompile pas,
#  il découpe le jar en couches et le pose sur un JRE minimal.
#
#    ./mvnw -DskipTests package
#    docker build -t netplus-back .
#    docker run --rm -p 8080:8080 \
#      -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/networkplan \
#      -e DB_PASSWORD=root netplus-back
#
#  Les couches Spring Boot (dependencies / spring-boot-loader /
#  snapshot-dependencies / application) font qu'un nouveau build ne
#  renvoie au registre que la couche « application », quelques centaines
#  de Ko, et non les 80 Mo du jar.
# ============================================================

# ---------- 1. découpe du jar en couches ----------
FROM eclipse-temurin:21-jre-alpine AS layers
WORKDIR /build
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted --launcher

# ---------- 2. image finale ----------
FROM eclipse-temurin:21-jre-alpine
LABEL org.opencontainers.image.title="netplus-back" \
      org.opencontainers.image.source="https://github.com/eskand/THENETWORKPLANBACK"

# Utilisateur non root : la NetworkPolicy et le SecurityContext du pod
# comptent dessus (runAsNonRoot).
RUN addgroup -S netplus && adduser -S -G netplus -h /app netplus
WORKDIR /app

COPY --from=layers --chown=netplus:netplus /build/extracted/dependencies/ ./
COPY --from=layers --chown=netplus:netplus /build/extracted/spring-boot-loader/ ./
COPY --from=layers --chown=netplus:netplus /build/extracted/snapshot-dependencies/ ./
COPY --from=layers --chown=netplus:netplus /build/extracted/application/ ./

USER netplus
EXPOSE 8080

# Le conteneur n'a que la mémoire de son cgroup : la JVM s'y adapte.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseZGC -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
