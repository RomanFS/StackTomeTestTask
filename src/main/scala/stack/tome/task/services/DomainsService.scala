package stack.tome.task.services

import cats.implicits.catsSyntaxOptionId
import stack.tome.task.models._
import zio._
import zio.concurrent.ConcurrentMap

trait DomainsService {
  def getAll: Task[Vector[Domain]]

  def addOrSet(newDomainInfo: Domain): Task[Unit]

  def deleteAll: Task[Unit]

}

object DomainsService {
  lazy val layer: ZLayer[DomainsDBService, Throwable, DomainsService] =
    ZLayer.fromZIO(
      ConcurrentMap
        .empty[String, DomainInfo]
        .map(reviewCountsMap =>
          new DomainsService {
            override def getAll: Task[Vector[Domain]] =
              for {
                result <- reviewCountsMap.toList.map(_.map(r => Domain(r._1, r._2)))
                _ <- ZIO.logDebug(s"DomainsService.getAll: $result")
              } yield result.toVector

            override def addOrSet(newDomainInfo: Domain): Task[Unit] =
              for {
                _ <- reviewCountsMap.compute(
                  newDomainInfo.name,
                  (_, oldInfoOp) =>
                    oldInfoOp match {
                      case Some(oldInfo) =>
                        oldInfo
                          .copy(
                            oldInfo.reviewsCount + newDomainInfo.info.reviewsCount,
                            newDomainInfo.info.newestReview orElse oldInfo.newestReview,
                          )
                          .some
                      case None => newDomainInfo.info.some
                    },
                )
                _ <- ZIO.logDebug(s"DomainsService.addOrSet: $newDomainInfo")
              } yield ()

            override def deleteAll: Task[Unit] =
              reviewCountsMap.removeIf((_, _) => true)

          }
        )
    )

  lazy val fakeLayer: ZLayer[Any, Nothing, DomainsService] =
    ZLayer.fromZIO(
      ZIO
        .logWarning("Fake DomainsService is used")
        .as(new DomainsService {
          override def getAll: Task[Vector[Domain]] = ZIO.succeed(Vector())

          override def addOrSet(newDomainInfo: Domain): Task[Unit] = ZIO.unit

          override def deleteAll: Task[Unit] = ZIO.unit

        })
    )

}
