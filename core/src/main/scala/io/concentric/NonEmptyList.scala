package io.concentric

/**
 * Minimal non-empty list.
 */
final case class NonEmptyList[+A](head: A, tail: List[A]) {
  def toList: List[A] = head :: tail
  def map[B](f: A => B): NonEmptyList[B] = NonEmptyList(f(head), tail.map(f))
  def ++[B >: A](other: NonEmptyList[B]): NonEmptyList[B] =
    NonEmptyList(head, tail ++ other.toList)
  def size: Int = 1 + tail.size
  def mkString(start: String, sep: String, end: String): String =
    toList.mkString(start, sep, end)
  def exists(p: A => Boolean): Boolean = (head == head && p(head)) || tail.exists(p)
  def toSet[B >: A]: Set[B] = toList.toSet
  def mapToList[B](f: A => B): List[B] = toList.map(f)
  def iterator: Iterator[A] = toList.iterator
  def foreach[B](f: A => B): Unit = { f(head); tail.foreach(f); () }
}

object NonEmptyList {
  def apply[A](head: A, tail: A*): NonEmptyList[A] = NonEmptyList(head, tail.toList)

  def fromIterableOption[A](as: Iterable[A]): Option[NonEmptyList[A]] =
    as.headOption.map(h => NonEmptyList(h, as.drop(1).toList))

  def fromIterableUnsafe[A](as: Iterable[A]): NonEmptyList[A] = {
    val it = as.iterator
    val h  = it.next()
    NonEmptyList(h, it.toList)
  }
}
