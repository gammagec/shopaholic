FROM openjdk:24-jdk-bullseye

WORKDIR /app

COPY build/libs /app/libs

EXPOSE 8080

ENTRYPOINT [
    "java",
    "--enable-native-access=ALL-UNNAMED",
    "-cp",
    "/app/libs/*",
    "com.christophergammage.shopaholic.ShopaholicServer"
]