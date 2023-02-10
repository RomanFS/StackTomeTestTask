package stack.tome.task

import org.http4s._
import org.http4s.implicits.http4sLiteralsSyntax
import zio._
import zio.interop.catz._

import scala.util.chaining.scalaUtilChainingOps

// TODO: add config
case class TrafficService(client: HttpClient) {
  private def trafficRequest(domain: String) = Request[Task](
    method = Method.GET,
    uri = uri"https://web.vstat.info/" / domain,
    headers = Headers(
      "authority" -> "web.vstat.info",
      "cookie" -> "", // TODO: get it from config
    ),
  )

  def getDomainTraffic(domain: String) =
    client(
      _.expect[String](trafficRequest(domain))
        .map("(?<=VISITS\" data-datum=\")\\d+".r.findFirstIn(_).flatMap(_.toIntOption).tap(r => println(s"$domain $r")))
    )

}

object TrafficService {
  lazy val layer =
    ZLayer.fromFunction(TrafficService.apply _)

  def getDomainTraffic(domain: String) =
    ZIO.serviceWithZIO[TrafficService](_.getDomainTraffic(domain))

}
