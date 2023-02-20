package stack.tome.task.services

import cats.implicits._
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import org.http4s._
import stack.tome.task.models.Review
import zio._
import zio.interop.catz._

import scala.util.chaining.scalaUtilChainingOps

trait ReviewsService {
  def getReviewsData(from: Option[Long] = None): ZIO[Any, Throwable, Vector[(String, Review)]]

}

object ReviewsService {
  lazy val layer: ZLayer[ConfigService with HttpClientService, ParseFailure, ReviewsService] =
    ZLayer.fromZIO(for {
      client <- ZIO.service[HttpClientService]
      config <- ZIO.service[ConfigService]
      categoriesUrl <- ZIO.fromEither(Uri.fromString(config.reviewsConfig.categoriesUrl))
      reviewsUrl <- ZIO.fromEither(Uri.fromString(config.reviewsConfig.reviewsUrl))
    } yield new ReviewsService {
      private lazy val categoriesRequest = Request[Task](uri = categoriesUrl)

      private def domainsRequest(category: String) =
        Request[Task](uri =
          (categoriesUrl / Uri.Path.Segment(category))
            .withQueryParam("sort", "latest_review")
        )

      private def recentReviewsRequest(reviewsId: String): Request[Task] =
        Request[Task](uri =
          (reviewsUrl / reviewsId / Uri.Path.Segment("reviews"))
            .withQueryParam("locale", "en-US")
        )

      private def parseCategories: Task[Vector[String]] =
        client(c =>
          for {
            result <- c
              .expect[String](categoriesUrl)
              .map(
                "(?<=/categories/)[A-z-]+"
                  .r
                  .findAllIn(_)
              )
            _ <- ZIO.logDebug(s"ReviewsService.parseCategories: ${result.toVector}")
          } yield result
            .distinct
            .toVector
        )

      private def parseDomainsData(category: String): Task[Iterator[(String, String)]] =
        client(c =>
          for {
            _ <- ZIO.logInfo(s"ReviewsService.parseDomainsData: parsing data for a category $category")
            result <- c
              .expect[String](domainsRequest(category))
              .map(
                "(?<=href=\"/review/)[^\"]+|(?<=latest-reviews-)[^-]+"
                  .r
                  .findAllIn(_)
              )
            _ <- ZIO.logDebug(s"ReviewsService.parseDomainsData category \"$category\": ${result.toVector}")
          } yield result
            .distinct
            .sliding(2, 2)
            .map(l => (l.head, l.last))
            .filter(_._2.lengthCompare(24) == 0)
        )

      private def getReviews(reviewsId: String): Task[Vector[Either[Throwable, Review]]] =
        client(c =>
          for {
            result <- c.expect[String](recentReviewsRequest(reviewsId))
            _ <- ZIO.logDebug(s"ReviewsService.getReviews reviewsId \"$reviewsId\": $result")
          } yield result
            .pipe(parse)
            .map(_ \\ "reviews")
            .traverse(
              _.headOption
                .flatMap(_.asArray)
                .toVector
                .flatten
                .map(_.as[Review].toOption.get)
            )
        ).retryN(3).catchAll(e => ZIO.logWarning(e.getMessage).as(Vector(Left(e))))

      override def getReviewsData(from: Option[Long] = None): ZIO[Any, Throwable, Vector[(String, Review)]] =
        for {
          categories <- parseCategories
          _ <- ZIO.logInfo(s"ReviewsService.getReviewsData: categories parsed at total of ${categories.length}")
          domains <- categories
            .parTraverse(parseDomainsData)
            .map(_.flatten)
          result <-
            domains
              .toVector
              .parFlatTraverse {
                case (domain, reviewsId) =>
                  getReviews(reviewsId)
                    .map(_.map(_.map(domain -> _)))
              }
              .map { reviewsData =>
                from match {
                  case Some(fromDate) =>
                    reviewsData.filter(_ match {
                      case Left(_) => false
                      case Right((_, review)) =>
                        review.date.createdAt.toEpochSecond > fromDate
                    })
                  case None => reviewsData
                }
              }
              .map(_.sequence)
              .flatMap(r => ZIO.fromEither(r))
          _ <- ZIO.logInfo(s"ReviewsService.getReviewsData: $result")
        } yield result

    })

  lazy val fakeLayer: ZLayer[Any, Nothing, ReviewsService] =
    ZLayer.fromZIO(
      ZIO
        .logWarning("Fake Reviews Service is used")
        .as(new ReviewsService {
          override def getReviewsData(
              from: Option[Long] = None
          ): ZIO[Any, Throwable, Vector[(String, Review)]] =
            ZIO.succeed(Vector())

        })
    )

  def getReviewsData(from: Option[Long] = None) = ZIO.serviceWithZIO[ReviewsService](_.getReviewsData(from))

}
