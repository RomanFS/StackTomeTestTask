package stack.tome.task.services

import zio._

import scala.collection.mutable
import scala.util.chaining.scalaUtilChainingOps

trait ReviewsCounterService {
  def getAll: Task[Vector[(String, Int)]]
  def addOrSet(domain: String, newReviewsCount: Int): Task[Unit]
  def deleteAll: Task[Unit]

}

object ReviewsCounterService {
  lazy val layer: ZLayer[ReviewCountsDBService, Throwable, ReviewsCounterService] =
    ZLayer.fromZIO(
      Ref
        .make(mutable.Map[String, Int]())
        .map(reviewCountsRef =>
          new ReviewsCounterService {
            override def getAll: Task[Vector[(String, Int)]] =
              reviewCountsRef.get.map(_.toVector.tap(r => println(s"ReviewsCounterService.getAll: $r")))

            override def addOrSet(domain: String, newReviewsCount: Int): Task[Unit] =
              for {
                _ <- reviewCountsRef.update { reviewCounts =>
                  reviewCounts.put(domain, reviewCounts.getOrElse(domain, 0) + newReviewsCount)
                  reviewCounts
                }
                _ <- getAll
              } yield ()

            override def deleteAll: Task[Unit] =
              reviewCountsRef.set(mutable.Map())

          }
        )
    )

  def getAll = ZIO.serviceWithZIO[ReviewsCounterService](_.getAll)
  def addOrSet(domain: String, newReviewsCount: Int) =
    ZIO.serviceWithZIO[ReviewsCounterService](_.addOrSet(domain, newReviewsCount))
  def removeAll = ZIO.serviceWithZIO[ReviewsCounterService](_.deleteAll)

}
