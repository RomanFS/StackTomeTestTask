package stack.tome.task

import cats.implicits._
import dev.profunktor.redis4cats.effect.Log.Stdout._
import dev.profunktor.redis4cats.{ Redis, RedisCommands }
import zio._
import zio.interop.catz._

trait DBService {
  def storeReviewCounts(reviewCounts: Vector[(String, Int)]): Task[Unit]
  def getReviewCounts: Task[Vector[(String, Int)]]

}

object DBService {
  lazy val redisLive: ZLayer[Any, Throwable, RedisService] =
    ZLayer.scoped(Redis[Task].utf8("redis://localhost").toScopedZIO.map(RedisService.apply))

  lazy val fake: ULayer[DBService] = ZLayer.succeed(new DBService {
    override def storeReviewCounts(reviewCounts: Vector[(String, Int)]): Task[Unit] = ZIO.unit

    override def getReviewCounts: Task[Vector[(String, Int)]] = ZIO.succeed(Vector(("someDomain", 1)))

  })

}

case class RedisService(redis: RedisCommands[Task, String, String]) extends DBService {
  override def storeReviewCounts(reviewCounts: Vector[(String, Int)]): Task[Unit] =
    reviewCounts
      .map {
        case (domain, count) => redis.set(domain, count.toString)
      }
      .sequence
      .as(ZIO.unit)

  // TODO: get only once per program launch
  override def getReviewCounts: Task[Vector[(String, Int)]] =
    for {
      keys <- redis.scan.map(_.keys.toVector)
      _ = println("must not appear twice")
      values <- keys.map(redis.get).sequence
    } yield (keys zip values).map(r => r._1 -> r._2.map(_.toInt).getOrElse(0))

}
