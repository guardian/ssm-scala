# SBT does not publish ARM images so emulate an x86 environment to allow local compilation on Macbooks.
FROM --platform=linux/amd64 sbtscala/scala-sbt:amazoncorretto-al2023-21.0.6_1.10.11_2.13.16 AS build

COPY . .
RUN sbt assembly

FROM amazoncorretto:24-al2023

COPY --from=build /root/target/scala-3.6.4/ssm.jar .
ENTRYPOINT [ "java", "-XX:UseSVE=0", "-jar", "ssm.jar" ]