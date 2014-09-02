package cc.factorie.app.nlp.pos

import cc.factorie._
import cc.factorie.app.nlp._
import cc.factorie.variable._

class PosDomain(elements: Vector[String],
                isNounFunc: (String) => (Boolean),
                isProperNounFunc: (String) => (Boolean),
                isAdjectiveFunc: (String) => (Boolean),
                isVerbFunc: (String) => (Boolean),
                isPersonalPronounFunc: (String) => (Boolean)) extends CategoricalDomain[String]{

  this ++= elements
  freeze()

  def isNoun(pos: String): Boolean = isNounFunc
  def isProperNoun(pos: String): Boolean = isProperNounFunc
  def isAdjective(pos: String): Boolean = isAdjectiveFunc
  def isVerb(pos: String): Boolean = isVerbFunc
  def isPersonalPronoun(pos: String): Boolean = isPersonalPronounFunc

}

class PosTag(val token: Token, initialValue: String, posDomain: PosDomain) extends CategoricalVariable(initialValue) {

  def domain = posDomain
  def isNoun: Boolean = domain.isNoun(categoryValue)
  def isProperNoun: Boolean = domain.isProperNoun(categoryValue)
  def isAdjective: Boolean = domain.isAdjective(categoryValue)
  def isVerb: Boolean = domain.isVerb(categoryValue)
  def isPersonalPronoun: Boolean = domain.isPersonalPronoun(categoryValue)
}

class LabeledPosTag(token: Token, targetValue: String, posDomain: PosDomain) extends PosTag(token, targetValue, posDomain) with CategoricalLabeling[String]

