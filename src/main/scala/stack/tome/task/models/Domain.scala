package stack.tome.task.models

case class Domain(name: String, info: DomainInfo)
case class DomainInfo(reviewsCount: Int = 0, newestReview: Option[Review] = None)
