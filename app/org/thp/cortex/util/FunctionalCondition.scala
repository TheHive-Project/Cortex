package org.thp.cortex.util

object FunctionalCondition {
  implicit class When[A](a: A) {
    def when(cond: Boolean)(whenTrue: A => A, whenFalse: A => A = identity): A = if (cond) whenTrue(a) else whenFalse(a)
  }
}
