FROM scratch

COPY ./build/native/nativeCompile/account-monitor /app/account-monitor

WORKDIR /app

EXPOSE 8080
EXPOSE 8081

ENTRYPOINT ["./account-monitor"]
