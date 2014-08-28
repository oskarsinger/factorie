/* Copyright (C) 2008-2010 University of Massachusetts Amherst,
   Department of Computer Science.
   This file is part of "FACTORIE" (Factor graphs, Imperative, Extensible)
   http://factorie.cs.umass.edu, http://code.google.com/p/factorie/
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package cc.factorie.app.nlp.pos
import cc.factorie._
import cc.factorie.app.nlp._
import cc.factorie.variable._

/** Penn Treebank part-of-speech tag domain. */
object CtbPosDomain extends CategoricalDomain[String] {
  this ++= Vector(
    "VA",
    "VC",
    "VE",
    "VV",
    "NR",
    "NT",
    "NN",
    "LC",
    "PN",
    "DT",
    "CD",
    "OD",
    "M",
    "AD",
    "P",
    "CC",
    "CS",
    "DEC",
    "DEG",
    "DER",
    "DEV",
    "SP",
    "AS",
    "ETC",
    "SP",
    "MSP",
    "IJ",
    "ON",
    "PU",
    "JJ",
    "FW",
    "LB",
    "SB",
    "BA"
  )
  freeze()

  def isNoun(pos: String): Boolean = pos(0) == 'N' 
  def isProperNoun(pos: String): Boolean = { pos == "NR" }
  def isVerb(pos: String): Boolean = pos(0) == 'V'
  def isAdjective(pos: String): Boolean = pos(0) == 'J'
  def isPersonalPronoun(pos: String): Boolean = pos == "PRP"

  /** A categorical variable, associated with a token, holding its Penn Treebank part-of-speech category.  */
  class CtbPosTag(val token:Token, initialValue:String) extends CategoricalVariable(initialValue) {
    def domain = CtbPosDomain
    def isNoun = this.domain.isNoun(categoryValue)
    def isProperNoun = this.domain.isProperNoun(categoryValue)
    def isVerb = this.domain.isVerb(categoryValue)
    def isAdjective = this.domain.isAdjective(categoryValue)
    def isPersonalPronoun = this.domain.isPersonalPronoun(categoryValue)
  }
  /** A categorical variable, associated with a token, holding its Penn Treebank part-of-speech category,
      which also separately holds its desired correct "target" value.  */
  class LabeledCtbPosTag(token: Token, targetValue: String) extends CtbPosTag(token, targetValue) with CategoricalLabeling[String]
}
