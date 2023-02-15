package stack.tome

import scala.concurrent.duration.{ Duration, FiniteDuration }

package object task {
  final implicit class DurationOps(val date: Duration) {
    def toFinite: FiniteDuration = FiniteDuration(date.length, date.unit)

  }

}
