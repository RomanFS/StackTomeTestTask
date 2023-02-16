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

trait ReviewsService {
  def getReviewsData(from: Option[ZonedDateTime] = None): ZIO[Any, Throwable, Vector[(String, Review)]]

}

object ReviewsService {
  lazy val layer: ZLayer[ConfigService with HttpClientService, ParseFailure, ReviewsService] =
    ZLayer.fromZIO(for {
      client <- ZIO.service[HttpClientService]
      config <- ZIO.service[ConfigService]
      domainsUrl <- ZIO.fromEither(Uri.fromString(config.reviewsConfig.domainsUrl))
      reviewsUrl <- ZIO.fromEither(Uri.fromString(config.reviewsConfig.reviewsUrl))
    } yield new ReviewsService {
      private lazy val domainsRequest = Request[Task](
        method = Method.GET,
        uri = domainsUrl,
        headers = Headers(),
      )

      private def recentReviewsRequest(reviewsId: String): Request[Task] = Request[Task](
        method = Method.GET,
        uri = (reviewsUrl / reviewsId / Uri.Path.Segment("reviews")).withQueryParam("locale", "en-US"),
        headers = Headers(),
      )

      private def parseDomainsData: Task[Iterator[(String, String)]] =
        client(c =>
          for {
            result <- c
              .expect[String](domainsRequest)
              .map(
                "(?<=href=\"/review/)[^\"]+|(?<=latest-reviews-)[^-]+"
                  .r
                  .findAllIn(_)
              )
            _ <- ZIO.logDebug(s"ReviewsService.parseDomainsData: $result")
          } yield result
            .distinct
            .sliding(2, 2)
            .map(l => (l.head, l.last))
            .filter(_._2.lengthCompare(24) == 0)
        )

      private def getReviews(reviewsId: String): Task[Vector[Either[ParsingFailure, Review]]] =
        client(c =>
          for {
            result <- c.expect[String](recentReviewsRequest(reviewsId))
            _ <- ZIO.logDebug(s"ReviewsService.getReviews: $result")
          } yield result
            .pipe(parse)
            .map(_ \\ "reviews")
            .traverse(_.headOption.flatMap(_.asArray).toVector.flatten.map(_.as[Review].toOption.get))
        )

      override def getReviewsData(
                                   from: Option[ZonedDateTime] = None
                                 ): ZIO[Any, Throwable, Vector[(String, Review)]] =
        for {
          domains <- parseDomainsData
          result <- ZIO.absolve(
            domains
              .take(config.maxDomainResponse)
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
          _ <- ZIO.logDebug(s"ReviewsService.getReviewsData: $result")
        } yield result

    })

  lazy val fakeLayer: ZLayer[Any, Nothing, ReviewsService] =
    ZLayer.fromZIO(
      ZIO
        .logWarning("Fake Reviews Service is used")
        .as(new ReviewsService {
          override def getReviewsData(
                                       from: Option[ZonedDateTime] = None
                                     ): ZIO[Any, Throwable, Vector[(String, Review)]] =
            ZIO.succeed(Vector())

        })
    )

  def getReviewsData(from: Option[ZonedDateTime] = None) = ZIO.serviceWithZIO[ReviewsService](_.getReviewsData(from))

}
