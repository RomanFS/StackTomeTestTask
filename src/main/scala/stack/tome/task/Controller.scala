package stack.tome.task

import cats.implicits._
import stack.tome.task.models._
import stack.tome.task.services._
import zio._
import zio.interop.catz._

import java.time.{ Clock, _ }

case class Controller(
    httpService: HttpService,
    reviewsService: ReviewsService,
    trafficService: TrafficService,
    domainsService: DomainsService,
    domainsDBService: DomainsDBService,
) {
  // TODO: change to a proper date value
  def startTime =
    ZonedDateTime.now(Clock.systemUTC()).minus(5.minutes)

  lazy val start =
    ( // start http service and schedule job for the Domain data gathering
      httpService.start <&> collectAndStoreDomainsData(startTime.some)
        .repeat(Schedule.fixed(5.minutes)) // TODO: use config value for scheduling
    ).ensuring(
      domainsService
        .getAll
        .flatMap(domainsDBService.storeDomains)
        .orDie
    )

  private def collectAndStoreDomainsData(from: => Option[ZonedDateTime] = None) =
    for {
      reviews <- reviewsService.getReviewsData(from)
      domainTrafficRequest = reviews
        .map(_._1)
        .traverse(domain => trafficService.getDomainTraffic(domain).map(_.getOrElse(0)).map(domain -> _))
      stateUpdate = reviews
        .groupBy(_._1)
        .toVector
        .traverse {
          case (domain, domainReviews) =>
            domainsService.addOrSet(
              Domain(
                domain,
                DomainInfo(
                  domainReviews.size,
                  domainReviews.map(_._2).sortBy(-_.date.createdAt.toInstant.toEpochMilli).headOption,
                ),
              )
            )
        }
      // TODO: save traffic data
      domainTrafficData <- domainTrafficRequest <& stateUpdate
    } yield domainTrafficData

}

object Controller {
  lazy val make =
    ZLayer.fromFunction(Controller.apply _)

}
