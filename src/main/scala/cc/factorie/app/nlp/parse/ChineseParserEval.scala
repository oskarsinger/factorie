/* Copyright (C) 2008-2014 University of Massachusetts Amherst.
   This file is part of "FACTORIE" (Factor graphs, Imperative, Extensible)
   http://factorie.cs.umass.edu, http://github.com/factorie
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package cc.factorie.app.nlp.parse

import cc.factorie.app.chineseStrings._

object ChineseParserEval {
  def calcUas(trees: Iterable[ChineseParseTree], includePunct: Boolean = false): Double = {
    var correct = 0.0
    var total = 0.0
    for (tree <- trees) {
      for (i <- 0 until tree.sentence.length) {
        if (includePunct || !isPunctuation(tree.sentence.tokens(i).string(0))) {
          total += 1
          if (tree._parents(i) == tree._targetParents(i)) correct += 1
        }
      }
    }
    correct / total
  }
  
  def calcLas(trees: Iterable[ChineseParseTree], includePunct: Boolean = false): Double = {
    var correct = 0.0
    var total = 0.0
    for (tree <- trees) {
      for (i <- 0 until tree.sentence.length) {
        if (includePunct || !isPunctuation(tree.sentence.tokens(i).string(0))) {
          total += 1
          if (tree._parents(i) == tree._targetParents(i) && tree._labels(i).valueIsTarget) correct += 1
        }
      }
    }
    correct / total
  }

}