package stack.tome.task

import cats.implicits._
import stack.tome.task.models._
import stack.tome.task.services._
import zio.{ Duration => ZDuration, _ }
import zio.interop.catz._

import java.time.{ Clock, _ }

case class Controller(
    httpService: HttpService,
    reviewsService: ReviewsService,
    trafficService: TrafficService,
    domainsService: DomainsService,
    domainsDBService: DomainsDBService,
    configService: ConfigService,
) {
  def startTime =
    ZonedDateTime.now(Clock.systemUTC()).minus(Duration.ofMillis(configService.updateInterval.toMillis))

  lazy val start =
    (
      for {
        _ <- ZIO.logDebug(s"Setup.configuration: $configService")
        // start http service and schedule job for the Domain data gathering
        _ <- httpService.start <&> collectAndStoreDomainsData(startTime.some)
          .repeat(Schedule.fixed(ZDuration(configService.updateInterval.length, configService.updateInterval.unit)))
      } yield ()
      ).ensuring(
      (for {
        domainsData <- domainsService.getAll
        _ <- domainsDBService.storeDomains(domainsData)
        _ <- ZIO.logInfo("Collected data was saved to the permanent storage.")
      } yield ()).orDie
    )

  private def collectAndStoreDomainsData(from: => Option[ZonedDateTime] = None) =
    for {
      _ <- ZIO.logInfo("Running Controller.collectAndStoreDomainsData")
      reviews <- reviewsService.getReviewsData(from)
      domainTrafficUpdate = reviews
        .map(_._1)
        .distinct
        .traverse(domain => trafficService.getDomainTraffic(domain).map(_.getOrElse(0)).map(domain -> _))
        .flatMap(domainsDBService.storeDomainTraffic)
      domainsUpdate = reviews
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
      _ <- domainTrafficUpdate <&> domainsUpdate
    } yield ()

}

object Controller {
  lazy val make =
    ZLayer.fromFunction(Controller.apply _)

}
