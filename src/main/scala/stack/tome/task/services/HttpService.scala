package stack.tome.task.services

import com.comcast.ip4s._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.server._
import org.http4s.implicits._
import stack.tome.task.models.Domain
import zio._
import zio.interop.catz._

case class HttpService(domainsService: DomainsService, domainsDBService: DomainsDBService) extends Http4sDsl[Task] {
  private lazy val helloWorldService = HttpRoutes
    .of[Task] {
      case GET -> Root / "domains" =>
        Ok(getResponse)
    }
    .orNotFound

  private def getResponse =
    (domainsService.getAll <&> domainsDBService.getDomains).map {
      case (newDomains, storedDomains) =>
        newDomains
          .map {
            case domain @ Domain(name, newInfo) =>
              storedDomains.find(_.name == name) match {
                case Some(Domain(_, oldInfo)) =>
                  domain.copy(info = oldInfo.copy(oldInfo.reviewsCount + newInfo.reviewsCount, newInfo.newestReview))
                case None => domain
              }
          }
          .asJson
          .noSpaces
    }

  lazy val start =
    EmberServerBuilder
      .default[Task]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(helloWorldService)
      .build
      .use(_ => ZIO.never)

}

object HttpService {
  lazy val make =
    ZLayer.fromFunction(HttpService.apply _)

}
