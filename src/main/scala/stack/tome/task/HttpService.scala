package stack.tome.task

import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import zio._
import zio.interop.catz._

// TODO: add config
trait HttpService {
  def apply[A](f: Client[Task] => Task[A]): Task[A]
}

object HttpService {
  lazy val live =
    ZLayer.scoped(
      EmberClientBuilder
        .default[Task]
        .build
        .toScopedZIO
        .map(client => new HttpService {
          override def apply[A](f: Client[Task] => Task[A]): Task[A] = f(client)
        })
    )

}
