package io.dsentric.validators

import io.dsentric.annotations.FieldValidator
import java.util.{List as JList}

/**
 * Built-in [[FieldValidator]] that rejects strings containing any whitespace
 * character (space, tab, newline, etc.).
 *
 * Useful for fields like usernames, slugs, or API keys where embedded spaces
 * are never valid.
 *
 * Usage:
 * {{{
 * @contract case class Slug(
 *   @validateWith(Array(classOf[NoWhitespaceValidator])) value: String
 * )
 * }}}
 */
class NoWhitespaceValidator extends FieldValidator[String]:
  def validate(value: String): JList[String] =
    if value != null && value.exists(_.isWhitespace) then
      JList.of(s"must not contain whitespace characters (got: '${value.take(40)}')")
    else
      JList.of()
