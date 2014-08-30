package cc.factorie.app.nlp.pos

import cc.factorie._
import cc.factorie.app.nlp._
import cc.factorie.variable._

class PosDomain {

  def isNoun: Boolean
  def isProperNoun: Boolean
  def isAdjective: Boolean
  def isVerb: Boolean
  def isPersonalPronoun: Boolean

  class Tag(val token: Token, initialValue: String) extends CategoricalVariable(initialValue)

  class LabeledTag(token: Token, targetValue: String) extends Tag(token, targetValue) with CategoricalLabeling[String]
}
