package stack.tome.task.models

import java.time.ZonedDateTime

case class Review(id: String, text: String, consumer: Consumer, date: DateData) {
  override def toString: String = s"Review($id, createdAt: ${date.createdAt})"
}
case class Consumer(displayName: String)
case class DateData(createdAt: ZonedDateTime)

case class ReviewResponse(id: String, text: String, createdAt: ZonedDateTime)
