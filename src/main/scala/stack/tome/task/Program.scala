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
        ReviewsService.layer,
        TrafficService.fakeLayer, // TODO: change to the real layer
        ReviewsCounterService.layer,
        HttpClientService.live,
        ReviewCountsDBService.redisLive,
      )

}
