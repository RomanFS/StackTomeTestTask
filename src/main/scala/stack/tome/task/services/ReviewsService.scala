package stack.tome.task.services

import cats.implicits._
import io.circe.generic.auto._
import io.circe.parser._
import org.http4s._
import stack.tome.task.models.Review
import zio._
import zio.interop.catz._

import scala.collection.mutable
import scala.util.chaining.scalaUtilChainingOps
import scala.util.matching.Regex

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

      private case class DomainData(reviewId: String = "", domainCategories: List[String] = Nil)

      private def groupDomainsResponseData(matchedResponse: List[Regex.Match]) = {
        val hashMap = mutable.HashMap[String, DomainData]()
        var currentDomain = ""

        matchedResponse.foreach(r =>
          if (r.group(1) != null)
            currentDomain = r.group(1)
          else if (currentDomain.nonEmpty) {
            val currOp = hashMap.get(currentDomain)
            if (r.group(2) != null)
              currOp match {
                case Some(currValue) => hashMap.update(currentDomain, currValue.copy(reviewId = r.group(2)))
                case None => hashMap.put(currentDomain, DomainData(reviewId = r.group(2)))
              }
            else if (r.group(3) != null)
              currOp match {
                case Some(currValue) =>
                  hashMap.update(
                    currentDomain,
                    currValue.copy(domainCategories = currValue.domainCategories :+ r.group(3)),
                  )

                case None => hashMap.put(currentDomain, DomainData(domainCategories = List(r.group(3))))

              }
          }
        )

        hashMap
          .filter(_._2.domainCategories.exists(_.toLowerCase.contains("store")))
          .map(r => r._1 -> r._2.reviewId)
          .toVector
      }

      private def parseDomainsData(category: String): Task[Vector[(String, String)]] =
        client(c =>
          for {
            _ <- ZIO.logInfo(s"ReviewsService.parseDomainsData: parsing data for a category $category")
            result <- c
              .expect[String](domainsRequest(category))
              .map { response =>
                "(?<=href=\"/review/)([^\"]+)|(?<=latest-reviews-)([^-]+)|(?<=AAY17\">)([\\w\\s]+)"
                  .r
                  .findAllIn(response)
                  .matchData
                  .toList
              }
            _ <- ZIO.logDebug(s"ReviewsService.parseDomainsData category \"$category\": $result")
          } yield result.pipe(groupDomainsResponseData)
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
            .take(3)
            .parTraverse(parseDomainsData)
            .map(_.flatten)
          result <-
            domains
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
