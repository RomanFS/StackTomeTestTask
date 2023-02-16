package stack.tome.task.services

import cats.implicits._
import com.comcast.ip4s._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.server._
import org.http4s.implicits._
import stack.tome.task.models._
import zio._
import zio.interop.catz._

case class HttpService(domainsService: DomainsService, domainsDBService: DomainsDBService, config: ConfigService)
    extends Http4sDsl[Task] {
  private lazy val domainsRoute = HttpRoutes
    .of[Task] {
      case GET -> Root / "domains" =>
        for {
          _ <- ZIO.logDebug("/domains is called")
          response <- Ok(getResponse)
          _ <- ZIO.logDebug(s"/domains returns $response")
        } yield response
    }
    .orNotFound

  private def getResponse =
    (domainsService.getAll <&> domainsDBService.getDomains).flatMap {
      case (newDomains, storedDomains) =>
        val domains = newDomains
          .map {
            case domain @ Domain(name, newInfo) =>
              val domainTotal = storedDomains.find(_.name == name) match {
                case Some(Domain(_, oldInfo)) =>
                  domain.copy(info =
                    oldInfo.copy(
                      oldInfo.reviewsCount + newInfo.reviewsCount,
                      newInfo.newestReview orElse oldInfo.newestReview,
                    )
                  )
                case None => domain
              }

              val reviewResponse = domainTotal
                .info
                .newestReview
                .map(review => ReviewResponse(review.id, review.text, review.date.createdAt))

              DomainResponse(name, newInfo.reviewsCount, domainTotal.info.reviewsCount, reviewResponse)
          }

        domainsDBService
          .getDomainsTraffic(domains.map(_.name))
          .map(
            _.flatMap {
              case (domainName, traffic) => domains.find(_.name == domainName).map(DomainWithTraffic(_, traffic))
            }
              .sortBy(_.traffic)
              .sortWith {
                case (d1, d2) => d1.domain.compareReviewsDates(d2.domain)
              }
              .take(config.maxDomainResponse)
              .asJson
              .noSpaces
          )
    }

  lazy val start =
    EmberServerBuilder
      .default[Task]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(domainsRoute)
      .build
      .use(_ => ZIO.never)
      .onInterrupt(ZIO.logInfo("HttpService was stopped."))

}

object HttpService {
  lazy val make =
    ZLayer.fromFunction(HttpService.apply _)

}
