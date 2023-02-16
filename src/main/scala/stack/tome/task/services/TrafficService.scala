package stack.tome.task.services

import cats.implicits._
import org.http4s._
import org.http4s.implicits.http4sLiteralsSyntax
import zio._
import zio.interop.catz._

trait TrafficService {
  def getDomainTraffic(domain: String): Task[Option[Int]]

}

object TrafficService {
  lazy val layer: ZLayer[HttpClientService with ConfigService, ParseFailure, TrafficService] =
    ZLayer.fromZIO(for {
      client <- ZIO.service[HttpClientService]
      config <- ZIO.service[ConfigService]
      trafficRequestUri <- ZIO.fromEither(Uri.fromString(s"https://${config.trafficConfig.trafficDomain}"))
      _ <- ZIO.logInfo(s"TrafficService.trafficRequest uri: $trafficRequestUri")
    } yield new TrafficService {
      private def trafficRequest(domain: String): Request[Task] =
        Request[Task](
          method = Method.GET,
          uri = trafficRequestUri / domain,
          headers = Headers(
            "authority" -> config.trafficConfig.trafficDomain,
            "cookie" -> config.trafficConfig.sessionToken,
          ),
        )

      override def getDomainTraffic(domain: String): Task[Option[Int]] =
        client(c =>
          for {
            result <- c
              .expect[String](trafficRequest(domain))
              .catchNonFatalOrDie {
                case e: Throwable => ZIO.logError(e.getMessage).as("")
              }
              .map(
                "(?<=VISITS\" data-datum=\")\\d+"
                  .r
                  .findFirstIn(_)
              )
            _ <- ZIO.logDebug(s"TrafficService.getDomainTraffic: $result")
          } yield result.flatMap(_.toIntOption)
        )

    })

  lazy val fakeLayer: ZLayer[Any, Nothing, TrafficService] =
    ZLayer.fromZIO(
      ZIO
        .logWarning("Fake TrafficService is used")
        .as(new TrafficService {
          override def getDomainTraffic(domain: String): Task[Option[RuntimeFlags]] =
            Random.nextIntBetween(1000, 10000).map(_.some)

        })
    )

  def getDomainTraffic(domain: String) =
    ZIO.serviceWithZIO[TrafficService](_.getDomainTraffic(domain))

}
