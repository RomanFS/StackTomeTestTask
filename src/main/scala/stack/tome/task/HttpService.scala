package stack.tome.task

import com.comcast.ip4s._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.server._
import org.http4s.implicits._
import zio._
import zio.interop.catz._

case class HttpService(reviewsCounterService: ReviewsCounterService, reviewCountsDBService: ReviewCountsDBService)
    extends Http4sDsl[Task] {
  private lazy val helloWorldService = HttpRoutes
    .of[Task] {
      case GET -> Root / "domains" =>
        Ok(getResponse)
    }
    .orNotFound

  private def getResponse =
    (reviewsCounterService.getAll <&> reviewCountsDBService.getReviewCounts).map {
      case (newReviewCounts, storedReviewCounts) =>
        newReviewCounts
          .map {
            case (domain, newCount) =>
              storedReviewCounts.find(_._1 == domain) match {
                case Some((_, storedCount)) => (domain, newCount, storedCount + newCount)
                case None => (domain, newCount, newCount)
              }
          }
          .toString()
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
