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

# Utilisateur non root, avec un uid/gid FIXE et NUMÉRIQUE (10001) : le pod
# Kubernetes tourne en runAsNonRoot, et kubelet refuse de démarrer un
# conteneur dont l'image déclare son utilisateur par un nom seulement
# (« image has non-numeric user, cannot verify user is non-root »).
# k8s/base/back.yaml pose le même 10001 en runAsUser.
RUN addgroup -S -g 10001 netplus && adduser -S -u 10001 -G netplus -h /app netplus
WORKDIR /app

COPY --from=layers --chown=netplus:netplus /build/extracted/dependencies/ ./
COPY --from=layers --chown=netplus:netplus /build/extracted/spring-boot-loader/ ./
COPY --from=layers --chown=netplus:netplus /build/extracted/snapshot-dependencies/ ./
COPY --from=layers --chown=netplus:netplus /build/extracted/application/ ./

USER 10001:10001
EXPOSE 8080

# Le conteneur n'a que la mémoire de son cgroup : la JVM s'y adapte.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseZGC -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
