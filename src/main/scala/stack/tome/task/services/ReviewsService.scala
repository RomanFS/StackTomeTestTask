package stack.tome.task.services

import cats.implicits._
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import org.http4s._
import org.http4s.implicits.http4sLiteralsSyntax
import stack.tome.task.models.Review
import zio._
import zio.interop.catz._

import java.time.ZonedDateTime
import scala.util.chaining.scalaUtilChainingOps

// TODO: add config
trait ReviewsService {
  def getReviewsData(from: Option[ZonedDateTime] = None): ZIO[Any, Throwable, Vector[(String, Review)]]

}

object ReviewsService {
  lazy val layer: ZLayer[HttpClientService, Nothing, ReviewsService] =
    ZLayer.fromFunction((client: HttpClientService) =>
      new ReviewsService {
        private lazy val domainsRequest = Request[Task](
          method = Method.GET,
          uri = uri"https://www.trustpilot.com/categories/jewelry_store?sort=latest_review",
          headers = Headers(),
        )

        private def recentReviewsRequest(reviewsId: String): Request[Task] = Request[Task](
          method = Method.GET,
          uri = (uri"https://www.trustpilot.com/api/categoriespages/" / reviewsId
            / Uri.Path.Segment("reviews")).withQueryParam("locale", "en-US"),
          headers = Headers(),
        )

        private def parseDomainsData: Task[Iterator[(String, String)]] =
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

        private def getReviews(reviewsId: String): Task[Vector[Either[ParsingFailure, Review]]] =
          client(
            _.expect[String](recentReviewsRequest(reviewsId))
              .map(
                _.pipe(parse)
                  .map(_ \\ "reviews")
                  .traverse(_.headOption.flatMap(_.asArray).toVector.flatten.map(_.as[Review].toOption.get))
              )
          )

        override def getReviewsData(
            from: Option[ZonedDateTime] = None
        ): ZIO[Any, Throwable, Vector[(String, Review)]] =
          for {
            domains <- parseDomainsData
            result <- ZIO.absolve(
              domains
                /*.take(10)*/ // TODO: remove it or change to maxDomains
                .toVector
                .flatTraverse {
                  case (domain, reviewsId) =>
                    getReviews(reviewsId)
                      .map(_.map(_.leftMap(e => new Throwable(e.message)).map(domain -> _)))
                }
                .map { reviewsData =>
                  from match {
                    case Some(fromDate) =>
                      reviewsData.filter(_ match {
                        case Left(_) => false
                        case Right((_, review)) => review.date.createdAt.isAfter(fromDate)
                      })
                    case None => reviewsData
                  }
                }
                .map(_.sequence)
            )
          } yield result

      }
    )

  lazy val fakeLayer: ZLayer[HttpClientService, Nothing, ReviewsService] =
    ZLayer.fromFunction((_: HttpClientService) =>
      new ReviewsService {
        println("Fake Reviews Service is used")

        override def getReviewsData(
            from: Option[ZonedDateTime] = None
        ): ZIO[Any, Throwable, Vector[(String, Review)]] =
          ZIO.succeed(Vector())

      }
    )

  def getReviewsData(from: Option[ZonedDateTime] = None) = ZIO.serviceWithZIO[ReviewsService](_.getReviewsData(from))

}
