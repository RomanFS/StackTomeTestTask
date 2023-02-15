package stack.tome.task.services

import cats.implicits._
import dev.profunktor.redis4cats.effect.Log.Stdout._
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import stack.tome.task.models.{Domain, DomainInfo}
import zio._
import zio.interop.catz._

import scala.util.chaining.scalaUtilChainingOps

trait DomainsDBService {
  def storeDomains(newReviewCounts: Vector[Domain]): Task[Unit]
  def getDomains: Task[Vector[Domain]]

}

object DomainsDBService {
  lazy val redisLive: ZLayer[Any, Throwable, RedisDomainsService] =
    ZLayer.scoped(Redis[Task].utf8("redis://localhost").toScopedZIO.map(RedisDomainsService.apply))

  lazy val fake: ULayer[DomainsDBService] = ZLayer.succeed(new DomainsDBService {
    println("Fake ReviewCountsDBService is used")

    override def storeDomains(newDomains: Vector[Domain]): Task[Unit] = ZIO.unit

    override def getDomains: Task[Vector[Domain]] =
      ZIO.succeed(Vector(Domain("someDomain", DomainInfo())))

  })

}

case class RedisDomainsService(redis: RedisCommands[Task, String, String]) extends DomainsDBService {
  override def storeDomains(newDomains: Vector[Domain]): Task[Unit] =
    newDomains
      .tap(r => println(s"storeReviewCounts: $r"))
      .traverse { domain =>
        for {
          oldJson <- redis.get(domain.name)
          oldDomainInfo = oldJson.flatMap(decode[DomainInfo](_).toOption)
          newDomainInfo = oldDomainInfo match {
            case Some(oldInfo) =>
              domain.info.copy(reviewsCount = oldInfo.reviewsCount + domain.info.reviewsCount, domain.info.newestReview)
            case None => domain.info
          }
          _ <- redis.set(domain.name, newDomainInfo.asJson.noSpaces)
        } yield ()
      }
      .as(ZIO.unit)

  override def getDomains: Task[Vector[Domain]] =
    for {
      keys <- redis.scan.map(_.keys.toVector)
      values <- keys.traverse(redis.get).map(_.flatMap(_.flatMap(decode[DomainInfo](_).toOption)))
    } yield (keys zip values).map(r => Domain(r._1, r._2)).tap(r => println(s"getReviewCounts: $r"))

}
