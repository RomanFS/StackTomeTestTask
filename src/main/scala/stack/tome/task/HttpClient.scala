package stack.tome.task

import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import zio._
import zio.interop.catz._

// TODO: add config
trait HttpClient {
  def apply[A](f: Client[Task] => Task[A]): Task[A]
}

object HttpClient {
  lazy val layer =
    ZLayer.scoped(
      EmberClientBuilder
        .default[Task]
        .build
        .toScopedZIO
        .map(client => new HttpClient {
          override def apply[A](f: Client[Task] => Task[A]): Task[A] = f(client)
        })
    )

}
