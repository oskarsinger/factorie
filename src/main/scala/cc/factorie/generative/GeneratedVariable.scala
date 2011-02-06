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

package cc.factorie.generative
import cc.factorie._
import cc.factorie.la._
import cc.factorie.util.Substitutions
import scala.collection.mutable.{HashSet,ArrayBuffer}

// A collection of abstract Variables (and a generic Template) for generative models (directed Bayesian networks, 
// as opposed to undirected in which there is not a DAG-shaped generative storyline).

/** A variable whose value has been generated by a probability distribution parameterized by some parent variables.
    May or may not be mutable. */
trait GeneratedVar extends Variable {
  val generativeTemplate: GenerativeTemplate
  def generativeFactor: generativeTemplate.FactorType 
  /*= {
    val factors = generativeTemplate.unroll1(this.asInstanceOf[generativeTemplate.ChildType])
    require(factors.size = 1)
    factors.head
  }*/
  //def pr = generativeTemplate.pr(generativeFactor.statistics)
  def pr = generativeTemplate.pr(generativeFactor.statistics.asInstanceOf[generativeTemplate.StatisticsType])
  //def pr = generativeFactor.template.pr(generativeFactor.statistics.asInstanceOf[generativeTemplate.StatisticsType])
  def logpr = math.log(pr)
  //def sampledValue: Value = generativeTemplate.sampledValue(generativeFactor.statistics)
  def sampledValue: Value = generativeTemplate.sampledValue(generativeFactor.statistics.asInstanceOf[generativeTemplate.StatisticsType]).asInstanceOf[Value] // TODO How to get rid of this cast
  /** The list of random variables on which the generation of this variable's value depends. 
      By convention the first variable of the generativeFactor is the child, 
      and the remainder are its parents. */
  def parents: Seq[Parameter] = generativeFactor.variables.filter(_ != this).asInstanceOf[Seq[Parameter]]
  /** The list of random variables on which the generation of this variable's value depends,
      either directly or via a sequence of deterministic variables.  Changes to these variables
      cause the value of this.pr to change. */
  def extendedParents: Seq[Parameter] = {
    val result = new ArrayBuffer[Parameter]
    result ++= parents
    for (parent <- parents) parent match {
      case gv:GeneratedVar if (gv.isDeterministic) => result ++= gv.extendedParents
      case _ => {}
    }
    result
  }
  /** Parents, jumping over and selecting from MixtureComponents's parents if necessary. */
  def generativeParents: Seq[Parameter] = {
    val p = parents
    this match {
      case self:MixtureGeneratedVar => parents.map(_ match { case mc:MixtureComponents[_] => mc(self.choice.intValue); case p:Parameter => p })
      case _ => parents
    }
  }
  /** Returns true if the value of this parameter is a deterministic (non-stochastic) function of its parents. */
  def isDeterministic = false
}


/** A GeneratedVar that is mutable and whose value may be changed by sampling. */
trait MutableGeneratedVar extends GeneratedVar with MutableVar {
  /** Sample a new value for this variable given only its parents. */
  def sampleFromParents(implicit d:DiffList = null): this.type = {
    set(generativeTemplate.sampledValue(generativeFactor.statistics.asInstanceOf[generativeTemplate.StatisticsType]).asInstanceOf[this.Value])(d)
    this
  }
  // TODO Can we get rid of cast?
}


// TODO Are these still necessary?  Consider deleting
trait RealGenerating {
  def sampleDouble: Double
  def pr(x:Double): Double
  def logpr(x:Double): Double
}
trait DiscreteGenerating {
  def length: Int
  def sampleInt: Int
  def pr(index:Int): Double
  def logpr(index:Int): Double
}
trait ProportionGenerating {
  def sampleProportions: Proportions
  def pr(p:Proportions): Double
  def logpr(p:Proportions): Double
}



// Templates

trait GenerativeFactor extends Factor {
  type StatisticsType <: cc.factorie.Statistics
  //type ChildType <: GenerativeVar
  override def template: GenerativeTemplate
  def sampledValue: Any
  def pr: Double
  def logpr: Double
  def child: GeneratedVar
  //def parents: Seq[Parameter]
  override def statistics: StatisticsType
  override def copy(s:Substitutions): GenerativeFactor
}

trait GenerativeTemplate extends Template {
  type TemplateType <: GenerativeTemplate
  //type ChildType = V
  type ChildType <: GeneratedVar
  //type StatisticsType = StatisticsType
  //type FactorType <: GenerativeTemplate.this.Factor
  //def unrollChil(v:V): TemplateType#FactorType
  //def unroll1(v:ChildType): Factor // = unrollChild(v)
  def logpr(s:StatisticsType): Double
  def logpr(s:cc.factorie.Statistics): Double = logpr(s.asInstanceOf[StatisticsType])
  //def logpr(v:ValuesType): Double = logpr(v.statistics)
  //def logpr(f:FactorType): Double = logpr(f.statistics.asInstanceOf[StatisticsType])
  def pr(s:StatisticsType): Double // = math.exp(logpr(s))
  def pr(s:cc.factorie.Statistics): Double = pr(s.asInstanceOf[StatisticsType])
  //def pr(v:ValuesType): Double = pr(v.statistics.asInstanceOf[StatisticsType])
  //def pr(f:FactorType): Double = pr(f.statistics.asInstanceOf[StatisticsType])
  def score(s:StatisticsType) = logpr(s)
  def sampledValue(s:StatisticsType): ChildType#Value
  def sampledValue(s:cc.factorie.Statistics): ChildType#Value = sampledValue(s.asInstanceOf[StatisticsType])
  //def sampledValue(v:ValuesType): V#Value = sampledValue(v.statistics.asInstanceOf[StatisticsType])
  //def sampledValue(f:FactorType): V#Value = sampledValue(f.statistics.asInstanceOf[StatisticsType])
}

abstract class GenerativeTemplateWithStatistics1[C<:GeneratedVar:Manifest] extends TemplateWithStatistics1[C] with GenerativeTemplate {
  thisTemplate =>
  type TemplateType <: GenerativeTemplateWithStatistics1[C]
  type ChildType = C
}

abstract class GenerativeTemplateWithStatistics2[C<:GeneratedVar:Manifest,P1<:Variable:Manifest] extends TemplateWithStatistics2[C,P1] with GenerativeTemplate {
  thisTemplate =>
  type TemplateType <: GenerativeTemplateWithStatistics2[C,P1]
  type ChildType = C
  /*class Factor(c:C, p1:P1) extends super.Factor(c, p1) with GenerativeFactor {
    type StatisticsType = thisTemplate.StatisticsType
    def sampledValue: C#Value = thisTemplate.sampledValue(thisTemplate.statistics(this.values))
    def pr: Double = thisTemplate.pr(thisTemplate.statistics(this.values))
    def logpr: Double = thisTemplate.logpr(thisTemplate.statistics(this.values))
    def child: C = _1
    //def parents: Seq[Parameter] = Seq(_2)
    override def statistics = thisTemplate.statistics(values)
    override def copy(s:Substitutions) = Factor(s.sub(_1), s.sub(_2))
  }*/
}

abstract class GenerativeTemplateWithStatistics3[C<:GeneratedVar:Manifest,P1<:Variable:Manifest,P2<:Variable:Manifest] extends TemplateWithStatistics3[C,P1,P2] with GenerativeTemplate {
  thisTemplate =>
  type TemplateType <: GenerativeTemplateWithStatistics3[C,P1,P2]
  type ChildType = C
  /*class Factor(c:C, p1:P1, p2:P2) extends super.Factor(c, p1, p2) with GenerativeFactor {
    type StatisticsType = thisTemplate.StatisticsType
    def sampledValue: C#Value = thisTemplate.sampledValue(thisTemplate.statistics(this.values))
    def pr: Double = thisTemplate.pr(thisTemplate.statistics(this.values))
    def logpr: Double = thisTemplate.logpr(thisTemplate.statistics(this.values))
    def child: C = _1
    //def parents: Seq[Parameter] = Seq(_2, _3)
    override def statistics = thisTemplate.statistics(values)
    override def copy(s:Substitutions[Variable]) = Factor(s.sub(_1), s.sub(_2), s.sub(_3))
  }*/
}

abstract class GenerativeTemplateWithStatistics4[C<:GeneratedVar:Manifest,P1<:Variable:Manifest,P2<:Variable:Manifest,P3<:Variable:Manifest] extends TemplateWithStatistics4[C,P1,P2,P3] with GenerativeTemplate {
  thisTemplate =>
  type TemplateType <: GenerativeTemplateWithStatistics4[C,P1,P2,P3]
  type ChildType = C
  /*class Factor(c:C, p1:P1, p2:P2, p3:P3) extends super.Factor(c, p1, p2, p3) with GenerativeFactor {
    type StatisticsType = thisTemplate.StatisticsType
    def sampledValue: C#Value = thisTemplate.sampledValue(thisTemplate.statistics(this.values))
    def pr: Double = thisTemplate.pr(thisTemplate.statistics(this.values))
    def logpr: Double = thisTemplate.logpr(thisTemplate.statistics(this.values))
    def child: C = _1
    //def parents: Seq[Parameter] = Seq(_2, _3, _4)
    override def statistics = thisTemplate.statistics(values)
    override def copy(s:Substitutions[Variable]) = Factor(s.sub(_1), s.sub(_2), s.sub(_3), s.sub(_4))
  }*/
}
