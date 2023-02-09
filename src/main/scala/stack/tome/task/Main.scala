package stack.tome.task

import zio._

object Main extends ZIOAppDefault {
  lazy val run = ???

  // TODO list:
  //  - pull reviews for each domain from a category page (don't forget to get domain name)
  //  - pull top 10 domains (by new reviews) traffic data
  //  - update reviews count for each domain
  //  - load saved reviews counts (from file/DB)
  //  - response data will be a sorted list (by new reviews count or traffic[low priority]) of domains (max 10 domains)
  //    which contain:
  //     - domain name
  //     - newest review
  //     - new reviews count
  //     - total reviews count for domain
  //     - domain traffic
  //  - service has to pull data every 5 minutes
  //  - on service termination (or job completion) save reviews counts

  // config:
  //  - session token
  //  - domain amount per request (default: 10)
  //  - update interval (default: 5 minutes)

  // optional:
  //  - use kafka
  //  - add load balancer for requests to reduce load peaks
  //  - configure docker to run the project
}
