package stack.tome.task.services

import stack.tome.task.models.{ Domain, DomainInfo }
import zio._

import scala.collection.mutable
import scala.util.chaining.scalaUtilChainingOps

trait DomainsService {
  def getAll: Task[Vector[Domain]]
  def addOrSet(newDomainInfo: Domain): Task[Unit]
  def deleteAll: Task[Unit]

}

object DomainsService {
  lazy val layer: ZLayer[DomainsDBService, Throwable, DomainsService] =
    ZLayer.fromZIO(
      Ref
        .make(mutable.Map[String, DomainInfo]())
        .map(reviewCountsRef =>
          new DomainsService {
            override def getAll: Task[Vector[Domain]] =
              reviewCountsRef
                .get
                .map(
                  _.toVector
                    .map(r => Domain(r._1, r._2))
                    .tap(r => println(s"ReviewsCounterService.getAll: $r"))
                )

            override def addOrSet(newDomainInfo: Domain): Task[Unit] =
              for {
                _ <- reviewCountsRef.update { reviewCounts =>
                  val storedValue = reviewCounts.getOrElse(newDomainInfo.name, DomainInfo())
                  val newInfo = storedValue
                    .copy(
                      storedValue.reviewsCount + newDomainInfo.info.reviewsCount,
                      newDomainInfo.info.newestReview orElse storedValue.newestReview
                    )
                  reviewCounts.put(newDomainInfo.name, newInfo)
                  reviewCounts
                }
                _ <- getAll
              } yield ()

            override def deleteAll: Task[Unit] =
              reviewCountsRef.set(mutable.Map())

          }
        )
    )

  def getAll = ZIO.serviceWithZIO[DomainsService](_.getAll)
  def addOrSet(newDomainInfo: Domain) =
    ZIO.serviceWithZIO[DomainsService](_.addOrSet(newDomainInfo))
  def removeAll = ZIO.serviceWithZIO[DomainsService](_.deleteAll)

}
