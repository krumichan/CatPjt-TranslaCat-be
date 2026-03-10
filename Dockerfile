# 1단계: 빌드 스테이지 (용량을 줄이기 위해 빌드와 실행 분리)
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Gradle 실행에 필요한 xargs(findutils) 설치
RUN apt-get update && apt-get install -y findutils && rm -rf /var/lib/apt/lists/*

# 빌드에 필요한 파일들 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 실행 권한 부여
RUN chmod +x ./gradlew

# 라이브러리 의존성 사전 다운로드
RUN ./gradlew dependencies --no-daemon

# 소스 복사
COPY src src

# 빌드
RUN ./gradlew bootJar -x test

# 2단계: 실행 스테이지
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# 빌드 스테이지에서 생성된 jar 파일만 가져오기
COPY --from=build /app/build/libs/*.jar app.jar

RUN mkdir -p /app/dictionaries
COPY sudachi/system_full.dic /app/dictionaries/system_full.dic

# 백엔드 포트 (보통 8080) 노출
EXPOSE 8080

# 앱 실행
ENTRYPOINT ["java", "-jar", "app.jar"]