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

package cc.factorie.app.nlp
import cc.factorie._
import scala.collection.mutable.ArrayBuffer

// TODO We should really remove the DiffList implicit argument because adding a Sentence to a Section cannot be undone.

/** A span of Tokens making up a sentence within a Section of a Document.
    A Sentence is a special case of a TokenSpan, stored in its Section, and available through the Section.sentences method.
    From the Sentence you can get its sequence of Tokens, the Section that contains it, and the Document that contains it.
    Sentences can be added (in order) to a Section, but not removed from a Section.
    The index of this Sentence into the sequence of Sentences in the Section is available as 'Sentence.indexInSection'.
    The annotation ParseTree is stored on a Sentence.
    Unlike other TokenSpans, constructing a Sentence automatically add it to its Sections.
    @author Andrew McCallum */
class Sentence(sec:Section, initialStart:Int, initialLength:Int) extends TokenSpan(sec, initialStart, initialLength) {
  /** Construct a new 0-length Sentence that begins just past the current last token of the Section, and add it to the Section automatically.
      This constructor is typically used when reading labeled training data one token at a time, where we need Sentence and Token objects. */
  def this(sec:Section) = this(sec, sec.length, 0)
  /** Construct a new Sentence  */
  def this(doc:Document) = this(doc.asSection)

  // Initialization
  if (!sec.document.annotators.contains(classOf[Sentence])) sec.document.annotators(classOf[Sentence]) = UnknownDocumentAnnotator.getClass
  sec.addSentence(this)
  private val _indexInSection: Int = sec.sentences.length - 1

  def indexInSection: Int = _indexInSection

  def contains(element:Token) = tokens.contains(element) // TODO Re-implement this to be faster avoiding search using token.stringStart bounds 

  // Parse attributes
  def parse = attr[cc.factorie.app.nlp.parse.ParseTree]
  def parseRootChild: Token = attr[cc.factorie.app.nlp.parse.ParseTree].rootChild

  // common labels
  def posTags[A <: pos.PosTag]: IndexedSeq[A] = tokens.map(_.posTag)
  def nerTags: IndexedSeq[ner.NerTag] = tokens.map(_.nerTag)
}


// Cubbie storage

class SentenceCubbie extends TokenSpanCubbie {
  def finishStoreSentence(s:Sentence): Unit = {}
  def storeSentence(s:Sentence): this.type = {
	storeTokenSpan(s) // also calls finishStoreTokenSpan(s)
    finishStoreSentence(s)
    this
  }
  def finishFetchSentence(s:Sentence): Unit = finishFetchTokenSpan(s)
  def fetchSentence(section:Section): Sentence = {
    val s = new Sentence(section, start.value, length.value)
    finishFetchSentence(s)
    s
  }
}

// To save the sentence with its parse tree use "new SentenceCubbie with SentenceParseTreeCubbie"
trait SentenceParseCubbie extends SentenceCubbie {
  val parse = CubbieSlot("parse", () => new cc.factorie.app.nlp.parse.ParseTreeCubbie)
  override def finishStoreSentence(s:Sentence): Unit = {
    super.finishStoreSentence(s)
    parse := parse.constructor().storeParseTree(s.parse)
  }
  override def finishFetchSentence(s:Sentence): Unit = {
    super.finishFetchSentence(s)
    s.attr += parse.value.fetchParseTree(s)
  }
}
