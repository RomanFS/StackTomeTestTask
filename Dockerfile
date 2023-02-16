FROM sbtscala/scala-sbt:eclipse-temurin-focal-11.0.17_8_1.8.2_2.13.10 AS build

ADD ./ ./

CMD sbt run