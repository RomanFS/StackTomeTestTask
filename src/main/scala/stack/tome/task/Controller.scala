package stack.tome.task

import cats.implicits._
import stack.tome.task.models._
import stack.tome.task.services._
import zio._
import zio.interop.catz._

case class Controller(
    httpService: HttpService,
    reviewsService: ReviewsService,
    trafficService: TrafficService,
    domainsService: DomainsService,
    domainsDBService: DomainsDBService,
    configService: ConfigService,
) {
  lazy val start =
    (
      for {
        _ <- ZIO.logDebug(s"Setup.configuration: $configService")
        startTime <- Clock
          .instant
          .map(
            _.minusMillis(configService.updateInterval.toMillis).getEpochSecond
          )
        // collect the older data from N minutes ago (specified in config)
        _ <- collectAndStoreDomainsData(configService.firstDataCollectTime.toSeconds.some)
        // start http service and schedule job for the Domain data gathering
        _ <- httpService.start <&> collectAndStoreDomainsData(startTime.some)
          .delay(Duration.fromScala(configService.updateInterval))
          .repeat(Schedule.fixed(Duration.fromScala(configService.updateInterval)))
      } yield ()
    ).ensuring(
      (for {
        domainsData <- domainsService.getAll
        _ <- domainsDBService.storeDomains(domainsData)
        _ <- ZIO.logInfo("Collected data was saved to the permanent storage.")
      } yield ()).orDie
    )

  private def collectAndStoreDomainsData(from: => Option[Long]) =
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
