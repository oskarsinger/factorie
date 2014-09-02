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
object CtbPosDomain extends PosDomain(
  Vector(
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
  ),
  (pos: String) => pos(0) == 'N',
  (pos: String) => pos == "NR",
  (pos: String) => pos(0) == 'V',
  (pos: String) => pos(0) == 'J',
  (pos: String) => pos == "PRP"
}

class CtbPosTag(val token: Token, initialValue: String) extends PosTag(token, initialValue, CtbPosDomain)

class LabeledCtbPosTag(val token: Token, targetValue: String) extends LabeledPosTag(token, targetValue, CtbPosDomain)
