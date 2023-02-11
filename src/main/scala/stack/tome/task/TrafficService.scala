package stack.tome.task

import org.http4s._
import org.http4s.implicits.http4sLiteralsSyntax
import zio._
import zio.interop.catz._

import scala.util.chaining.scalaUtilChainingOps

// TODO: add config
trait TrafficService {
  def getDomainTraffic(domain: String): Task[Option[Int]]

}

object TrafficService {
  lazy val layer: ZLayer[HttpClient, Nothing, TrafficService] =
    ZLayer.fromFunction((client: HttpClient) =>
      new TrafficService {
        private def trafficRequest(domain: String): Request[Task] =
          Request[Task](
            method = Method.GET,
            uri = uri"https://web.vstat.info/" / domain,
            headers = Headers(
              "authority" -> "web.vstat.info",
              "cookie" -> "", // TODO: get it from config
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
                  .tap(r => println(s"$domain $r"))
              )
          )

      }
    )

  lazy val fakeLayer: ZLayer[HttpClient, Nothing, TrafficService] =
    ZLayer.fromZIO(
      ZIO
        .service[HttpClient]
        .map(_ =>
          new TrafficService {
            override def getDomainTraffic(domain: String): Task[Option[RuntimeFlags]] = ZIO.succeed(Some(666))

          }
        )
    )

  def getDomainTraffic(domain: String) =
    ZIO.serviceWithZIO[TrafficService](_.getDomainTraffic(domain))

}
