package stack.tome.task

import cats.implicits._
import zio.interop.catz._

object Program {
  lazy val live =
    for {
      reviewsData <- ReviewsService.getReviewsData
      trafficForEachDomain <- reviewsData.map(_._1).map(TrafficService.getDomainTraffic).sequence
    } yield () // TODO: map data and implement other functional

}
