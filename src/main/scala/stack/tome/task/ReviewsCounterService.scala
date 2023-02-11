package stack.tome.task

import zio._

import scala.collection.mutable
import scala.util.chaining.scalaUtilChainingOps

trait ReviewsCounterService {
  def getAll: Task[Vector[(String, Int, Int)]]
  def addOrSet(domain: String, newReviewsCount: Int): Task[Unit]
  def save: Task[Unit]

}

object ReviewsCounterService {
  lazy val layer: ZLayer[DBService, Throwable, ReviewsCounterService] =
    ZLayer.fromZIO(
      for {
        dbService <- ZIO.service[DBService]
        reviewCountsRef <- Ref.make(mutable.Map[String, Int]())
      } yield new ReviewsCounterService {
        override def getAll: Task[Vector[(String, Int, Int)]] =
          for {
            storedCounts <- dbService.getReviewCounts.map(_.tap(println))
            newCountsMap <- reviewCountsRef.get.map(_.toVector)
          } yield newCountsMap.map {
            case (domain, newCount) =>
              storedCounts.find(_._1 == domain) match {
                case Some((_, storedCount)) => (domain, newCount, storedCount + newCount)
                case None => (domain, newCount, newCount)
              }
          }

        override def addOrSet(domain: String, newReviewsCount: Int): Task[Unit] =
          reviewCountsRef.update { reviewCounts =>
            reviewCounts.put(domain, reviewCounts.getOrElse(domain, 0) + newReviewsCount)
            reviewCounts
          }

        override def save: Task[Unit] =
          for {
            reviewCounts <- reviewCountsRef.get.map(_.toVector)
            _ <- dbService.storeReviewCounts(reviewCounts)
            _ <- reviewCountsRef.set(mutable.Map())
          } yield ()

      }
    )

  def getAll = ZIO.serviceWithZIO[ReviewsCounterService](_.getAll)
  def addOrSet(domain: String, newReviewsCount: Int) =
    ZIO.serviceWithZIO[ReviewsCounterService](_.addOrSet(domain, newReviewsCount))
  def save = ZIO.serviceWithZIO[ReviewsCounterService](_.save)

}
