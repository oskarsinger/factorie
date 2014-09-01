package cc.factorie.app.nlp.pos

import cc.factorie._
import cc.factorie.app.nlp._
import cc.factorie.variable._

class PosDomain extends CategoricalDomain[String]{

  private val self = this
  def isNoun(pos: String): Boolean
  def isProperNoun(pos: String): Boolean
  def isAdjective(pos: String): Boolean
  def isVerb(pos: String): Boolean
  def isPersonalPronoun(pos: String): Boolean

  class Tag(val token: Token, initialValue: String) extends CategoricalVariable(initialValue) {

    def isNoun: Boolean = self.isNoun(categoryValue)
    def isProperNoun: Boolean = self.isProperNoun(categoryValue)
    def isAdjective: Boolean = self.isAdjective(categoryValue)
    def isVerb: Boolean = self.isVerb(categoryValue)
    def isPersonalPronoun: Boolean = self.isPersonalPronoun(categoryValue)
  }

  class LabeledTag(token: Token, targetValue: String) extends Tag(token, targetValue) with CategoricalLabeling[String]
}
