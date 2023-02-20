package stack.tome.task.services

import cats.implicits._
import dev.profunktor.redis4cats.effect.Log.Stdout._
import dev.profunktor.redis4cats.effects._
import dev.profunktor.redis4cats.{ Redis, RedisCommands }
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import stack.tome.task.DurationOps
import stack.tome.task.models._
import zio._
import zio.interop.catz._

trait DomainsDBService {
  def storeDomains(newReviewCounts: Vector[Domain]): Task[Unit]
  def getDomains: Task[Vector[Domain]]
  def storeDomainTraffic(domainTraffics: Vector[(String, Int)]): Task[Unit]
  def getDomainsTraffic(domains: Vector[String]): Task[Vector[(String, Int)]]

}

object DomainsDBService {
  lazy val redisLive: ZLayer[ConfigService, Throwable, RedisDomainsService] =
    ZLayer.scoped(for {
      host <- System.envOrElse("REDIS_SERVER", "localhost")
      _ <- ZIO.logDebug(s"Using host = $host, for redis.")
      redis <- Redis[Task].utf8(s"redis://$host").toScopedZIO
      config <- ZIO.service[ConfigService]
    } yield RedisDomainsService(redis, config))

  lazy val fake: ULayer[DomainsDBService] = ZLayer.fromZIO(
    ZIO
      .logWarning("Fake ReviewCountsDBService is used")
      .as(new DomainsDBService {
        override def storeDomains(newDomains: Vector[Domain]): Task[Unit] = ZIO.unit

        override def getDomains: Task[Vector[Domain]] =
          ZIO.succeed(Vector(Domain("someDomain", DomainInfo())))

        override def storeDomainTraffic(domainTraffics: Vector[(String, Int)]): Task[Unit] = ZIO.unit

        override def getDomainsTraffic(domains: Vector[String]): Task[Vector[(String, Int)]] =
          domains
            .traverse(domain =>
              Random
                .nextIntBetween(1000, 100000)
                .map(domain -> _)
            )

      })
  )

}

case class RedisDomainsService(redis: RedisCommands[Task, String, String], config: ConfigService)
    extends DomainsDBService {
  private val trafficKeyPostfix = "_traffic"

  override def storeDomains(newDomains: Vector[Domain]): Task[Unit] =
    newDomains
      .traverse { domain =>
        for {
          oldJson <- redis.get(domain.name)
          oldDomainInfo = oldJson.flatMap(decode[DomainInfo](_).toOption)
          newDomainInfo = oldDomainInfo match {
            case Some(oldInfo) =>
              domain
                .info
                .copy(
                  reviewsCount = oldInfo.reviewsCount + domain.info.reviewsCount,
                  domain.info.newestReview orElse oldInfo.newestReview,
                )
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
      _ <- ZIO.logDebug(s"DomainsDBService.getDomains: ${(keys zip values).map(r => Domain(r._1, r._2))}")
    } yield (keys zip values).map(r => Domain(r._1, r._2))

  override def storeDomainTraffic(domainTraffics: Vector[(String, Int)]): Task[Unit] =
    domainTraffics
      .traverse {
        case (domain, traffic) =>
          for {
            _ <- ZIO.logDebug(s"DomainsDBService.storeDomainTraffic: $domainTraffics")
            _ <- redis.set(
              domain + trafficKeyPostfix,
              traffic.toString,
              SetArgs(ex = SetArg.Existence.Nx, ttl = SetArg.Ttl.Ex(config.trafficConfig.trafficExpiration.toFinite)),
            )
          } yield ()
      }
      .as(ZIO.unit)

  override def getDomainsTraffic(domains: Vector[String]): Task[Vector[(String, Int)]] =
    for {
      _ <- ZIO.logDebug(s"DomainsDBService.getDomainsTraffic: $domains")
      result <- domains.flatTraverse(domain =>
        redis.get(domain + trafficKeyPostfix).map(_.map(_.toInt).map(domain -> _).toVector)
      )
    } yield result

}
