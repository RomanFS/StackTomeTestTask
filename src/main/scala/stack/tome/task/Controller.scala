package stack.tome.task

import cats.implicits._
import zio._
import zio.interop.catz._

import java.time.{ Clock, _ }

case class Controller(
    reviewsService: ReviewsService,
    trafficService: TrafficService,
    reviewsCounterService: ReviewsCounterService,
    reviewCountsDBService: ReviewCountsDBService,
) {
  // TODO: change to a proper date value
  lazy val startTime =
    ZonedDateTime.now(Clock.systemUTC()).minus(3.minutes)

  lazy val start =
    (for {
      _ <- retrieveDomainData(startTime.some).repeat(Schedule.fixed(3.minutes)) // TODO: use config value
      combinedCounts <- reviewsCounterService.getAll <&> reviewCountsDBService.getReviewCounts
    } yield {
      val (newReviewCounts, storedReviewCounts) = combinedCounts

      newReviewCounts.map {
        case (domain, newCount) =>
          storedReviewCounts.find(_._1 == domain) match {
            case Some((_, storedCount)) => (domain, storedCount + newCount)
            case None => (domain, newCount)
          }
      }
    }).flatMap(reviewCountsDBService.storeReviewCounts)

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
