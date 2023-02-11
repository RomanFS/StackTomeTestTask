package stack.tome.task

import cats.implicits._
import io.circe._
import io.circe.parser._
import org.http4s._
import org.http4s.implicits.http4sLiteralsSyntax
import zio._
import zio.interop.catz._

import scala.util.chaining.scalaUtilChainingOps

// TODO: add config
trait ReviewsService {
  def getReviewsData: ZIO[Any, Throwable, Vector[(String, Either[ParsingFailure, List[Json]])]]

}

object ReviewsService {
  lazy val layer: ZLayer[HttpClient, Nothing, ReviewsService] =
    ZLayer.fromFunction((client: HttpClient) =>
      new ReviewsService {
        private lazy val domainsRequest = Request[Task](
          method = Method.GET,
          uri = uri"https://www.trustpilot.com/categories/jewelry_store?sort=latest_review",
          headers = Headers(),
        )

        private def recentReviewsRequest(reviewsId: String) = Request[Task](
          method = Method.GET,
          uri = (uri"https://www.trustpilot.com/api/categoriespages/" / reviewsId
            / Uri.Path.Segment("reviews")).withQueryParam("locale", "en-US"),
          headers = Headers(),
        )

        private def parseDomainsData =
          client(
            _.expect[String](domainsRequest)
              .map(
                "(?<=href=\"/review/)[^\"]+|(?<=latest-reviews-)[^-]+"
                  .r
                  .findAllIn(_)
                  .distinct
                  .sliding(2, 2)
                  .map(l => (l.head, l.last))
                  .filter(_._2.lengthCompare(24) == 0)
              )
          )

        private def parseReviewsData(reviewsId: String) =
          client(
            _.expect[String](recentReviewsRequest(reviewsId))
              .map(
                _.pipe(parse)
                  .map(_ \\ "reviews")
                  .map(_.headOption.flatMap(_.asArray).toList.flatten)
              )
          )

        override def getReviewsData: ZIO[Any, Throwable, Vector[(String, Either[ParsingFailure, List[Json]])]] =
          for {
            domains <- parseDomainsData
            result <- domains
              .take(3) // TODO: remove it or change to maxDomains
              .map {
                // TODO: send to kafka
                case (domain, reviewsId) =>
                  parseReviewsData(reviewsId)
                    .map(domain -> _)
              }
              .toVector
              .sequence
              .map(_.tap(println))
          } yield result

      }
    )

  lazy val fakeLayer: ZLayer[HttpClient, Nothing, ReviewsService] =
    ZLayer.fromFunction((_: HttpClient) =>
      new ReviewsService {
        override def getReviewsData: ZIO[Any, Throwable, Vector[(String, Either[ParsingFailure, List[Json]])]] =
          ZIO.succeed(Vector())

      }
    )

  def getReviewsData = ZIO.serviceWithZIO[ReviewsService](_.getReviewsData)

}
