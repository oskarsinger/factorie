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
import cc.factorie._
import cc.factorie.app.nlp._
import java.lang.StringBuffer
import collection.mutable.ArrayBuffer
import cc.factorie.util.Cubbie
import cc.factorie.variable.{LabeledCategoricalVariable, EnumDomain}

// Representation for a dependency parse

// TODO I think this should instead be "ParseEdgeLabelDomain". -akm
object ChineseParseTreeLabelDomain extends EnumDomain {
  val tmp, loc, ext, ccjtn, tpc, cjtn, dir, prd, mnr, nmod, prt, prp, cjtn8, cjtn9, relc, cjtn7, cjtn4, cjtn5,
  root, cjtn3, cjtn0, cjtn1, adv, obj, comp, cjtn6, cnd, other, io, foc, aux, unk, bnf, cjt, dmod,
  voc, cjtn2, app, amod, sbj, lgs = Value
  index("") // necessary for empty categories
  freeze()
  def defaultCategory = "unk"
  def defaultIndex = index(defaultCategory)
}
// TODO I think this should instead be "ParseEdgeLabels extends LabeledCategoricalSeqVariable". -akm
class ChineseParseTreeLabel(val tree:ChineseParseTree, targetValue:String = ChineseParseTreeLabelDomain.defaultCategory) extends LabeledCategoricalVariable(targetValue) { def domain = ChineseParseTreeLabelDomain }

object ChineseParseTree {
  val rootIndex = -1
  val noIndex = -2
}

// TODO This initialization is really inefficient.  Fix it. -akm
class ChineseParseTree(val sentence:Sentence, theTargetParents:Array[Int], theTargetLabels:Array[Int]) {
  def this(sentence:Sentence) = this(sentence, Array.fill[Int](sentence.length)(ChineseParseTree.noIndex), Array.fill(sentence.length)(ChineseParseTreeLabelDomain.defaultIndex)) // Note: this puts in dummy target data which may be confusing
  def this(sentence:Sentence, theTargetParents:Seq[Int], theTargetLabels:Seq[String]) = this(sentence, theTargetParents.toArray, theTargetLabels.map(c => ChineseParseTreeLabelDomain.index(c)).toArray)
  def check(parents:Array[Int]): Unit = {
    val l = parents.length; var i = 0; while (i < l) {
      require(parents(i) < l)
      i += 1
    }
  }
  check(theTargetParents)
  val _labels = theTargetLabels.map(s => new ChineseParseTreeLabel(this, ChineseParseTreeLabelDomain.category(s))).toArray
  val _parents = { val p = new Array[Int](theTargetParents.length); System.arraycopy(theTargetParents, 0, p, 0, p.length); p }
  val _targetParents = theTargetParents
  def labels: Array[ChineseParseTreeLabel] = _labels
  def parents: Array[Int] = _parents
  def targetParents: Array[Int] = _targetParents
  def setParentsToTarget(): Unit = System.arraycopy(_targetParents, 0, _parents, 0, _parents.length)
  def numParentsCorrect: Int = { var result = 0; for (i <- 0 until _parents.length) if (_parents(i) == _targetParents(i)) result += 1; result }
  def parentsAccuracy: Double = numParentsCorrect.toDouble / _parents.length
  def numLabelsCorrect: Int = {var result = 0; for (i <- 0 until _labels.length) if (_labels(i).valueIsTarget) result += 1; result }
  def labelsAccuracy: Double = numLabelsCorrect.toDouble / _labels.length
  /** Returns the position in the sentence of the root token. */ 
  def rootChildIndex: Int = firstChild(-1)
  /** Return the token at the root of the parse tree.  The parent of this token is null.  The parentIndex of this position is -1. */
  def rootChild: Token = sentence.tokens(rootChildIndex)
  /** Make the argument the root of the tree.  This method does not prevent their being two roots. */
  def setRootChild(token:Token): Unit = setParent(token.position - sentence.start, -1)
  /** Returns the sentence position of the parent of the token at position childIndex */
  def parentIndex(childIndex:Int): Int = if (childIndex == ChineseParseTree.rootIndex) ChineseParseTree.noIndex else _parents(childIndex)
  def targetParentIndex(childIndex:Int): Int = if (childIndex == ChineseParseTree.rootIndex) ChineseParseTree.noIndex else _targetParents(childIndex)
  /** Returns the parent token of the token at position childIndex (or null if the token at childIndex is the root) */
  def parent(childIndex:Int): Token = {
    val idx = _parents(childIndex)
    if (idx == -1) null // -1 is rootIndex
    else sentence.tokens(idx)
  }
  /** Returns the parent token of the given token */
  def parent(token:Token): Token = { require(token.sentence eq sentence); parent(token.position - sentence.start) }
  /** Set the parent of the token at position 'child' to be at position 'parentIndex'.  A parentIndex of -1 indicates the root.  */
  def setParent(childIndex:Int, parentIndex:Int): Unit = _parents(childIndex) = parentIndex
  def setTargetParent(childIndex:Int, parentIndex:Int): Unit = _targetParents(childIndex) = parentIndex
  /** Set the parent of the token 'child' to be 'parent'. */
  def setParent(child:Token, parent:Token): Unit = {
    require(child.sentence eq sentence)
    if (parent eq null) {
      _parents(child.position - sentence.start) = -1
    } else {
      require(parent.sentence eq sentence)
      _parents(child.position - sentence.start) = parent.position - sentence.start
    }
  }

  //TODO: all of the  following methods are inefficient if the parse tree is fixed, and various things
  //can be precomputed.

  /** Return the sentence index of the first token whose parent is 'parentIndex' */
  private def firstChild(parentIndex:Int): Int = {
    var i = 0
    while ( i < _parents.length) {
      if (_parents(i) == parentIndex) return i
      i += 1
    }
    -1
  }


  /** Return a list of tokens who are the children of the token at sentence position 'parentIndex' */
  def children(parentIndex:Int): Seq[Token] = {
    getChildrenIndices(parentIndex).map(i => sentence.tokens(i))
  }

  def getChildrenIndices(parentIndex:Int, filter : Int => Boolean = {x => false}): Seq[Int] = {
    val result = new ArrayBuffer[Int]
    var i = 0
    while (i < _parents.length) {
      if (_parents(i) == parentIndex) result += i
      i += 1
    }
    result.sorted.takeWhile( i => !filter(i))
  }

  def subtree(parentIndex:Int): Seq[Token] = {
    getSubtreeInds(parentIndex).map(sentence.tokens(_))
  }

  def getSubtreeInds(parentIndex: Int, filter : Int => Boolean = {x => false}): Seq[Int] = {
    val result = new ArrayBuffer[Int]()
    result += parentIndex
    result ++= getChildrenIndices(parentIndex, filter).flatMap(getSubtreeInds(_)).distinct
    result
  }

  def leftChildren(parentIndex:Int): Seq[Token] = {
    val result = new scala.collection.mutable.ArrayBuffer[Token]
    var i = 0
    while (i < parentIndex) {
      if (_parents(i) == parentIndex) result += sentence.tokens(i)
      i += 1
    }
    result
  }
  def rightChildren(parentIndex:Int): Seq[Token] = {
    val result = new scala.collection.mutable.ArrayBuffer[Token]
    var i = parentIndex+1
    while (i < _parents.length) {
      if (_parents(i) == parentIndex) result += sentence.tokens(i)
      i += 1
    }
    result
  }
  /** Return a list of tokens who are the children of parentToken */
  //def children(parentToken:Token): Seq[Token] = children(parentToken.position - sentence.start)
  /** Return a list of tokens who are the children of the token at sentence position 'parentIndex' and who also have the indicated label value. */
  def childrenLabeled(index:Int, labelIntValue:Int): Seq[Token] = {
    val result = new scala.collection.mutable.ArrayBuffer[Token]
    var i = 0
    while (i < _parents.length) {
      if (_parents(i) == index && _labels(i).intValue == labelIntValue) result += sentence.tokens(i)
      i += 1
    }
    result
  }
  def leftChildrenLabeled(parentIndex:Int, labelIntValue:Int): Seq[Token] = {
    val result = new scala.collection.mutable.ArrayBuffer[Token]
    var i = 0
    while (i < parentIndex) {
      if (_parents(i) == parentIndex && _labels(i).intValue == labelIntValue) result += sentence.tokens(i)
      i += 1
    }
    result
  }
  def rightChildrenLabeled(parentIndex:Int, labelIntValue:Int): Seq[Token] = {
    val result = new scala.collection.mutable.ArrayBuffer[Token]
    var i = parentIndex+1
    while (i < _parents.length) {
      if (_parents(i) == parentIndex && _labels(i).intValue == labelIntValue) result += sentence.tokens(i)
      i += 1
    }
    result
  }
  //def childrenOfLabel(token:Token, labelIntValue:Int): Seq[Token] = childrenOfLabel(token.position - sentence.start, labelIntValue)
  //def childrenLabeled(index:Int, labelValue:DiscreteValue): Seq[Token] = childrenLabeled(index, labelValue.intValue) 
  //def childrenOfLabel(token:Token, labelValue:DiscreteValue): Seq[Token] = childrenOfLabel(token.position - sentence.start, labelValue.intValue)
  /** Return the label on the edge from the child at sentence position 'index' to its parent. */
  def label(index:Int): ChineseParseTreeLabel = _labels(index)
  def copy: ChineseParseTree = {
    val newTree = new ChineseParseTree(sentence, targetParents, labels.map(_.target.categoryValue))
    for (i <- 0 until sentence.length) {
      newTree._parents(i) = this._parents(i)
      newTree._labels(i).set(this._labels(i).intValue)(null)
    }
    newTree
  }
  /** Return the label on the edge from 'childToken' to its parent. */
  //def label(childToken:Token): ParseTreeLabel = { require(childToken.sentence eq sentence); label(childToken.position - sentence.start) }
  override def toString: String = {
    val tokenStrings = {
      if (sentence.tokens.forall(_.chinesePosTag ne null))
        sentence.tokens.map(t => t.string + "/" + t.chinesePosTag.categoryValue)
      else
        sentence.tokens.map(_.string)
    }
    val labelStrings = _labels.map(_.value.toString())
    val buff = new StringBuffer()
    for (i <- 0 until sentence.length)
      buff.append(i + " " + _parents(i) + " " + tokenStrings(i) + " " + labelStrings(i) + "\n")
    buff.toString
  }

  def toStringTex:String = {
    def texEdges(idx:Int, builder:StringBuilder):StringBuilder = this.children(idx) match {
      case empty if empty.isEmpty => builder
      case children => children.foreach { token =>
        val childIdx = token.positionInSentence
        val parentIdx = token.parseParentIndex
        val label = token.parseLabel.categoryValue
        builder.append("  \\depedge{%s}{%s}{%s}".format(parentIdx + 1, childIdx + 1, label)).append("\n") // latex uses 1-indexing
        texEdges(childIdx, builder)
      }
        builder
    }
    val sentenceString = this.sentence.tokens.map(_.string).mkString(""" \& """) + """\\"""

    val rootId = this.rootChildIndex
    val rootLabel = this.label(rootId).categoryValue // should always be 'root'
    val rootString = "  \\deproot{%s}{%s}".format(rootId, rootLabel)

    val sb = new StringBuilder
    sb.append("""\begin{dependency}""").append("\n")
    sb.append("""  \begin{deptext}""").append("\n")
    sb.append(sentenceString).append("\n")
    sb.append("""  \end{deptext}""").append("\n")
    sb.append(rootString).append("\n")
    texEdges(rootId, sb)
    sb.append("""\end{dependency}""").append("\n")

    sb.toString()
  }

}

// Example usages:
// token.sentence.attr[ParseTree].parent(token)
// sentence.attr[ParseTree].children(token)
// sentence.attr[ParseTree].setParent(token, parentToken)
// sentence.attr[ParseTree].label(token)
// sentence.attr[ParseTree].label(token).set("SUBJ")

// Methods also created in Token supporting:
// token.parseParent
// token.setParseParent(parentToken)
// token.parseChildren
// token.parseLabel
// token.leftChildren

class ChineseParseTreeCubbie extends Cubbie {
  val parents = IntListSlot("parents")
  val labels = StringListSlot("labels")
  def newParseTree(s:Sentence): ChineseParseTree = new ChineseParseTree(s) // This will be abstract when ParseTree domain is unfixed
  def storeParseTree(pt:ChineseParseTree): this.type = {
    parents := pt.parents
    labels := pt.labels.map(_.categoryValue)
    this
  }
  def fetchParseTree(s:Sentence): ChineseParseTree = {
    val pt = newParseTree(s)
    for (i <- 0 until s.length) {
      pt.setParent(i, parents.value(i))
      pt.label(i).setCategory(labels.value(i))(null)
    }
    pt
  }
}