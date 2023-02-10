package stack.tome.task

import cats.effect._
import cats.implicits._
import io.circe.parser._
import org.http4s._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.implicits.http4sLiteralsSyntax

import scala.util.chaining.scalaUtilChainingOps

// TODO: change to ZIO and make it layer
object HttpClient extends IOApp {
  lazy val httpClient = EmberClientBuilder
    .default[IO]
    .build
    .use { client =>
      for {
        domains <- client
          .expect[String](domainsRequest)
          .map(
            "(?<=href=\"/review/)[^\"]+|(?<=latest-reviews-)[^-]+"
              .r
              .findAllIn(_)
              .distinct
              .sliding(2, 2)
              .map(l => (l.head, l.last))
              .filter(_._2.lengthCompare(24) == 0)
              .tap(println)
          )
        _ <- domains
          .take(1)
          .map {
            // TODO: use domain
            case ((domain, reviewsId)) =>
              client
                .expect[String](recentReviewsRequest(reviewsId))
                .map(
                  _.pipe(parse)
                    .map(_ \\ "reviews")
                    .map(_.headOption.flatMap(_.asArray).toList.flatten)
                    // TODO: send to kafka
                    .tap(println)
                )
          }
          .toVector
          .sequence
        // TODO: add domain traffic request here?
      } yield ()
    }

  lazy val domainsRequest = Request[IO](
    method = Method.GET,
    uri = uri"https://www.trustpilot.com/categories/jewelry_store?sort=latest_review",
    headers = Headers(),
  )

  def recentReviewsRequest(reviewsId: String) = Request[IO](
    method = Method.GET,
    uri = (uri"https://www.trustpilot.com/api/categoriespages/" / reviewsId
      / Uri.Path.Segment("reviews")).withQueryParam("locale", "en-US"),
    headers = Headers(),
  )

  override def run(args: List[String]): IO[ExitCode] = httpClient.as(ExitCode.Success)

}
