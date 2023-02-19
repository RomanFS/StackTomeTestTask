package stack.tome.task.services

import zio.config._
import ConfigDescriptor._

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

final case class ConfigService(
    updateInterval: Duration,
    firstDataCollectTime: Duration,
    maxDomainResponse: Int,
    reviewsConfig: ReviewsConfig,
    trafficConfig: TrafficConfig,
)
final case class ReviewsConfig(domainsUrl: String, reviewsUrl: String)
final case class TrafficConfig(trafficDomain: String, sessionToken: String, trafficExpiration: Duration)

object ConfigService {
  val reviews =
    (string("domains_url") zip string("reviews_url")).to[ReviewsConfig]

  val traffic =
    (string("traffic_domain") zip string("session_token") zip
      duration("traffic_data_expiration").default(
        Duration(1, TimeUnit.HOURS)
      )).to[TrafficConfig]

  val general =
    duration("update_interval").default(5, TimeUnit.MINUTES) zip
      duration("first_data_collect_time").default(1, TimeUnit.HOURS) zip
      int("max_domain_response").default(10)

  val configuration =
    (general zip reviews zip traffic).to[ConfigService]

  lazy val layer =
    ZConfig.fromPropertiesFile("application.conf", configuration)

}
