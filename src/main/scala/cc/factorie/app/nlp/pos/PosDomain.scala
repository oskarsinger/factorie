package cc.factorie.app.nlp.pos

import cc.factorie._
import cc.factorie.app.nlp._
import cc.factorie.variable._

class PosDomain(elements: Vector[String]) extends CategoricalDomain[String]{

  this ++= elements
  freeze()

  def isNoun(pos: String): Boolean
  def isProperNoun(pos: String): Boolean
  def isAdjective(pos: String): Boolean
  def isVerb(pos: String): Boolean
  def isPersonalPronoun(pos: String): Boolean

}
object PennPosDomain extends PosDomain(
  Vector(
      "#", // In WSJ but not in Ontonotes
      "$",
      "''",
      ",",
      "-LRB-",
      "-RRB-",
      ".",
      ":",
      "CC",
      "CD",
      "DT",
      "EX",
      "FW",
      "IN",
      "JJ",
      "JJR",
      "JJS",
      "LS",
      "MD",
      "NN",
      "NNP",
      "NNPS",
      "NNS",
      "PDT",
      "POS",
      "PRP",
      "PRP$",
      "PUNC",
      "RB",
      "RBR",
      "RBS",
      "RP",
      "SYM",
      "TO",
      "UH",
      "VB",
      "VBD",
      "VBG",
      "VBN",
      "VBP",
      "VBZ",
      "WDT",
      "WP",
      "WP$",
      "WRB",
      "``",
      "ADD", // in Ontonotes, but not WSJ
      "AFX", // in Ontonotes, but not WSJ
      "HYPH", // in Ontonotes, but not WSJ
      "NFP", // in Ontonotes, but not WSJ
      "XX" // in Ontonotes, but not WSJ
  )
)

class PosTag(val token: Token, initialValue: String, posDomain: PosDomain) extends CategoricalVariable(initialValue) {

  def domain = posDomain
  def isNoun: Boolean = domain.isNoun(categoryValue)
  def isProperNoun: Boolean = domain.isProperNoun(categoryValue)
  def isAdjective: Boolean = domain.isAdjective(categoryValue)
  def isVerb: Boolean = domain.isVerb(categoryValue)
  def isPersonalPronoun: Boolean = domain.isPersonalPronoun(categoryValue)
}

class LabeledPosTag(token: Token, targetValue: String, posDomain: PosDomain) extends PosTag(token, targetValue, posDomain) with CategoricalLabeling[String]
