package com.gu.ssm


object UI {
  def printMetadata(text: String): Unit = {
    System.err.println(text.colour(Console.CYAN))
  }

  def printErr(text: String): Unit = {
    System.err.println(text.colour(Console.YELLOW))
  }

  implicit class RichString(val s: String) extends AnyVal {
    def colour(colour: String): String = {
      colour + s + Console.RESET
    }
  }
}
