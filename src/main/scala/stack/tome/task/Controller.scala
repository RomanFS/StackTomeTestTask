package stack.tome.task

import cats.implicits._
import zio._
import zio.interop.catz._

import java.time.{ Clock, _ }

case class Controller(
    httpService: HttpService,
    reviewsService: ReviewsService,
    trafficService: TrafficService,
    reviewsCounterService: ReviewsCounterService,
    reviewCountsDBService: ReviewCountsDBService,
) {
  // TODO: change to a proper date value
  lazy val startTime =
    ZonedDateTime.now(Clock.systemUTC()).minus(3.days)

  lazy val start =
    // TODO: use config value for scheduling
    (httpService.start <&> retrieveDomainData(startTime.some).repeat(Schedule.fixed(10.seconds))).ensuring(
      reviewsCounterService
        .getAll
        .flatMap(reviewCountsDBService.storeReviewCounts)
        .orDie
    )

  private def retrieveDomainData(from: Option[ZonedDateTime] = None) =
    for {
      reviewsData <- reviewsService.getReviewsData(from)
      domainTrafficRequest = reviewsData
        .map(_._1)
        .map(domain => trafficService.getDomainTraffic(domain).map(domain -> _))
        .sequence
      stateUpdate = reviewsData.map {
        case (domain, reviews) =>
          // TODO: ignore if no reviews?
          reviewsCounterService.addOrSet(domain, reviews.size)
      }.sequence
      // TODO: move to kafka consumer
      domainTrafficData <- domainTrafficRequest.map(_.map {
        case (domain, trafficOp) => domain -> trafficOp.getOrElse(0)
      }) <& stateUpdate
    } yield domainTrafficData

}

object Controller {
  lazy val make =
    ZLayer.fromFunction(Controller.apply _)

}
