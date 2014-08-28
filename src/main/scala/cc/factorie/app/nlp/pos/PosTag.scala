package cc.factorie.app.nlp.pos

class PosDomain extends CategoricalDomain[String] {
  def isNoun(pos: String): Boolean
  def isProperNoun(pos: String): Boolean
  def isVerb(pos: String): Boolean
  def isAdjective(pos: String): Boolean
  def isPersonalPronoun(pos: String): Boolean

  class PosTag(val token: Token, initialValue: String) extends CategoricalVariable(initialValue) {
    def domain
    
    def 
  }
}
