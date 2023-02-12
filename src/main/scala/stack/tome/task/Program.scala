package stack.tome.task

import zio._

object Program {
  lazy val make =
    ZIO
      .service[Controller]
      .provide(
        Controller.make,
        ReviewsService.layer,
        TrafficService.fakeLayer, // TODO: change to the real layer
        ReviewsCounterService.layer,
        HttpClientService.live,
        ReviewCountsDBService.redisLive,
      )

}
