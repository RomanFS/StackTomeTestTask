package stack.tome.task

import java.time.ZonedDateTime

case class Review(id: String, text: String, consumer: Consumer, date: DateData)
case class Consumer(displayName: String)
case class DateData(createdAt: ZonedDateTime)
