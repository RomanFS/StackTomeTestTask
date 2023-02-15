package stack.tome.task.services

import cats.implicits._
import org.http4s._
import org.http4s.implicits.http4sLiteralsSyntax
import zio._
import zio.interop.catz._

// TODO: add config
trait TrafficService {
  def getDomainTraffic(domain: String): Task[Option[Int]]

}

object TrafficService {
  lazy val layer: ZLayer[HttpClientService with ConfigService, Nothing, TrafficService] =
    ZLayer.fromFunction((client: HttpClientService, config: ConfigService) =>
      new TrafficService {
        private def trafficRequest(domain: String): Request[Task] =
          Request[Task](
            method = Method.GET,
            uri = uri"https://" / config.trafficConfig.trafficDomain / domain,
            headers = Headers(
              "authority" -> config.trafficConfig.trafficDomain,
              "cookie" -> config.trafficConfig.sessionToken,
            ),
          )

        override def getDomainTraffic(domain: String): Task[Option[Int]] =
          client(
            _.expect[String](trafficRequest(domain))
              .map(
                "(?<=VISITS\" data-datum=\")\\d+"
                  .r
                  .findFirstIn(_)
                  .flatMap(_.toIntOption)
              )
          )

      }
    )

  lazy val fakeLayer: ZLayer[HttpClientService, Nothing, TrafficService] =
    ZLayer.fromZIO(
      ZIO
        .service[HttpClientService]
        .as(new TrafficService {
          println("Fake TrafficService is used")

          override def getDomainTraffic(domain: String): Task[Option[RuntimeFlags]] =
            Random.nextIntBetween(1000, 10000).map(_.some)

        })
    )

  def getDomainTraffic(domain: String) =
    ZIO.serviceWithZIO[TrafficService](_.getDomainTraffic(domain))

}
