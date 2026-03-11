FROM gradle:7.6-jdk11 AS build
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon || true
COPY src ./src
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:11-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

RUN apt-get update && apt-get install -y \
    libx11-6 libxcomposite1 libxdamage1 libxrandr2 \
    libgbm1 libasound2t64 libpangocairo-1.0-0 libatk1.0-0 \
    libatk-bridge2.0-0 libcups2 libdrm2 libnspr4 libnss3 \
    libxss1 libxfixes3 libxkbcommon0 fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/*

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]