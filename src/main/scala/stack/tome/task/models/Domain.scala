package stack.tome.task.models

case class Domain(name: String, info: DomainInfo)
case class DomainInfo(reviewsCount: Int = 0, newestReview: Option[Review] = None)

case class DomainWithTraffic(domain: DomainResponse, traffic: Int)
case class DomainResponse(name: String, newReviews: Int, totalReviews: Int, review: Option[ReviewResponse]) {
  def compareReviewsDates(d2: DomainResponse): Boolean =
    (review, d2.review) match {
      case (Some(r1), Some(r2)) => r1.createdAt.isAfter(r2.createdAt)
      case (Some(_), _) => true
      case (_, Some(_)) => false
      case _ => true
    }

}
