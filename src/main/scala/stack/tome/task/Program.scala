package stack.tome.task

import stack.tome.task.services._
import zio._

object Program {
  lazy val start =
    ZIO
      .serviceWithZIO[Controller](_.start)
      .provide(
        Controller.make,
        HttpService.make,
        ConfigService.layer,
        ReviewsService.layer,
        TrafficService.fakeLayer,
        DomainsService.layer,
        HttpClientService.live,
        DomainsDBService.redisLive,
      )

}
