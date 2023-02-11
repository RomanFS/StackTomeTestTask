package stack.tome.task

import cats.implicits._
import zio._
import zio.interop.catz._

case class Controller(
    reviewsService: ReviewsService,
    trafficService: TrafficService,
    reviewsCounterService: ReviewsCounterService,
) {
  lazy val start =
    for {
      reviewsData <- reviewsService.getReviewsData
      trafficForEachDomain <- reviewsData.map(_._1).map(trafficService.getDomainTraffic).sequence
      _ <- reviewsData.map {
        case (domain, reviews) =>
          reviewsCounterService.addOrSet(domain, reviews.size)
      }.sequence
      result <- reviewsCounterService.getAll
      _ <- reviewsCounterService.save
    } yield ()

}

object Controller {
  lazy val make =
    ZLayer.fromFunction(Controller.apply _)

}
