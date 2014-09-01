package cc.factorie.app.nlp.phrase

import cc.factorie.app.nlp._

import java.io._
import cc.factorie.util.{HyperparameterMain, BinarySerializer}
import cc.factorie.variable._
import cc.factorie.optimize.Trainer
import cc.factorie.app.chain.ChainModel
import cc.factorie.app.chain.Observations._
import scala.io.Source
import cc.factorie.app.nlp.load._
import cc.factorie.app.nlp.pos.PennPosDomain


/**
 * User: cellier
 * Date: 10/7/13
 * Time: 2:49 PM
 * Chunker based on Sha & Pereira '03 using a linear chain crf.
 */

 /*
 * Takes as a type parameter an extension from load.Load2000.ChunkTag
 * BILOUChunkTag and BIOChunkTag can be trained using conll2000 data
 * NestedChunkTag requires custom data tagged in the BILOUNestedChunkDomain notation
 * For NP retrieval of the tags generated by this class, app.nlp.mention.NPChunkMentionFinder can be used
 */
class ChainChunker[L<:ChunkTag](chunkDomain: CategoricalDomain[String], newChunkLabel: (Token) => L)(implicit m: Manifest[L]) extends DocumentAnnotator {
  def process(document: Document) = {
    document.sentences.foreach(s => {
      if (s.nonEmpty) {
        s.tokens.foreach(t => if (!t.attr.contains(m.runtimeClass)) t.attr += newChunkLabel(t))
        features(s)
        model.maximize(s.tokens.map(_.attr[L]))(null)
      }
    })
    document
  }
  def prereqAttrs = Seq(classOf[Token], classOf[Sentence],classOf[PennPosDomain.Tag])
  def postAttrs = Seq(m.runtimeClass)
  def tokenAnnotationString(token: Token) = { val label = token.attr[L]; if (label ne null) label.categoryValue else "(null)" }

  def serialize(stream: OutputStream) {
    import cc.factorie.util.CubbieConversions._
    val dstream = new DataOutputStream(stream)
    BinarySerializer.serialize(ChunkFeaturesDomain.dimensionDomain, dstream)
    BinarySerializer.serialize(model, dstream)
    dstream.close()
  }
  def deserialize(stream: InputStream) {
    import cc.factorie.util.CubbieConversions._
    val dstream = new DataInputStream(stream)
    BinarySerializer.deserialize(ChunkFeaturesDomain.dimensionDomain, dstream)
    BinarySerializer.deserialize(model, dstream)
    dstream.close()
  }

  def train(trainSentences:Seq[Sentence], testSentences:Seq[Sentence], useFullFeatures:Boolean = false, lrate:Double = 0.1, decay:Double = 0.01, cutoff:Int = 2, doBootstrap:Boolean = true, useHingeLoss:Boolean = false, numIterations: Int = 5, l1Factor:Double = 0.000001, l2Factor:Double = 0.000001)(implicit random: scala.util.Random) {
    ChunkFeaturesDomain.setFeatureSet(useFullFeatures)
    trainSentences.foreach(s=>features(s))
    print("Features for Training Generated: ")
    if(useFullFeatures) println("Full Set") else println("Subset Set")
    ChunkFeaturesDomain.freeze()
    testSentences.foreach(features)

    def evaluate() {
      (trainSentences ++ testSentences).foreach(s => model.maximize(s.tokens.map(_.attr[L]))(null))
      val segmentEvaluation = new cc.factorie.app.chain.SegmentEvaluation[L](chunkDomain.categories.filter(_.length > 2).map(_.substring(2)))
      for (sentence <- testSentences) segmentEvaluation += sentence.tokens.map(_.attr[L])
      println(segmentEvaluation)
      println("Train accuracy: "+ HammingObjective.accuracy(trainSentences.flatMap(s => s.tokens.map(_.attr[L]))))
      println("Test accuracy: "+ HammingObjective.accuracy(testSentences.flatMap(s => s.tokens.map(_.attr[L]))))
    }
    val examples = trainSentences.map(sentence => new model.ChainStructuredSVMExample(sentence.tokens.map(_.attr[L]))).toSeq
    val optimizer = new cc.factorie.optimize.AdaGradRDA(rate=lrate, l1=l1Factor/examples.length, l2=l2Factor/examples.length)
    Trainer.onlineTrain(model.parameters, examples, maxIterations=numIterations, optimizer=optimizer, evaluate=evaluate, useParallelTrainer = false)
  }

  object ChunkFeaturesDomain extends CategoricalVectorDomain[String]{var fullFeatureSet: Boolean = false; def setFeatureSet(full:Boolean){fullFeatureSet = full}}

  class ChunkFeatures(val token:Token) extends BinaryFeatureVectorVariable[String] { def domain = ChunkFeaturesDomain; override def skipNonCategories = true }

  val model = new ChainModel[ChunkTag, ChunkFeatures, Token](chunkDomain,
    ChunkFeaturesDomain,
    l => l.token.attr[ChunkFeatures],
    l => l.token,
    t => t.attr[L]){
    useObsMarkov = false
  }

  def features(sentence: Sentence): Unit = {
    import cc.factorie.app.strings.simplifyDigits
    val tokens = sentence.tokens.zipWithIndex
    for ((token,i) <- tokens) {
      if(token.attr[ChunkFeatures] ne null)
        token.attr.remove[ChunkFeatures]
      val features = token.attr += new ChunkFeatures(token)
      val rawWord = token.string
      val posTag = token.attr[PennPosDomain.Tag]
      features += "SENTLOC="+i
      features += "P="+posTag
      features += "Raw="+rawWord
      val shape = cc.factorie.app.strings.stringShape(rawWord, 2)
      features += "WS="+shape
      if (token.isPunctuation) features += "PUNCTUATION"
      if(ChunkFeaturesDomain.fullFeatureSet){
        val word = simplifyDigits(rawWord).toLowerCase
        if (word.length > 5) { features += "P="+cc.factorie.app.strings.prefix(word, 4); features += "S="+cc.factorie.app.strings.suffix(word, 4) }
        features += "STEM=" + cc.factorie.app.strings.porterStem(word)
        features += "WSIZE=" + rawWord.length

      }
      features += "BIAS"
    }
    addNeighboringFeatureConjunctions(sentence.tokens, (t: Token) => t.attr[ChunkFeatures], "W=[^@]*$", List(-2), List(-1), List(1),List(2), List(-1,0), List(0,1))
    addNeighboringFeatureConjunctions(sentence.tokens, (t: Token) => t.attr[ChunkFeatures], "P=[^@]*$", List(-2), List(-1), List(1), List(2), List(-2,-1), List(-1,0), List(0,1), List(1,2),List(-2,-1,0),List(-1,0,1),List(0,1,2))
  }
}

object BILOUChainChunker extends ChainChunker[BILOUChunkTag](BILOUChunkDomain.dimensionDomain, (t) => new BILOUChunkTag(t,"O")) {
  deserialize(new FileInputStream(new java.io.File("BILOUChainChunker.factorie")))
}

object BIOChainChunker extends ChainChunker[BIOChunkTag](BIOChunkDomain.dimensionDomain, (t) => new BIOChunkTag(t,"O")) {
  deserialize(new FileInputStream(new java.io.File("BIOChainChunker.factorie")))
}

object NestedChainChunker extends ChainChunker[BILOUNestedChunkTag](BILOUNestedChunkDomain.dimensionDomain, (t) => new BILOUNestedChunkTag(t,"O:O"))
{
  deserialize(new FileInputStream(new java.io.File("NESTEDChainChunker.factorie")))
}


/*
 * By Default:
 *   Takes conll2000 BIO tagged data as input
 *   Coverts to and trains on BILOU encoding
 */
object ChainChunkerTrainer extends HyperparameterMain {
  def generateErrorOutput(sentence: Sentence): String ={
    val sb = new StringBuffer
    sentence.tokens.map{t=>sb.append("%s %20s %10s %10s  %s\n".format(if (t.attr.all[ChunkTag].head.valueIsTarget) " " else "*", t.string, t.attr[PennPosDomain.Tag], t.attr.all[ChunkTag].head.target.categoryValue, t.attr.all[ChunkTag].head.categoryValue))}.mkString("\n")
  }

  def evaluateParameters(args: Array[String]): Double = {
    implicit val random = new scala.util.Random(0)
    val opts = new ChunkerOpts
    opts.parse(args)
    assert(opts.trainFile.wasInvoked)
    val chunk = opts.trainingEncoding.value match {
      case "BILOU" => new ChainChunker[BILOUChunkTag](BILOUChunkDomain.dimensionDomain, (t) => new BILOUChunkTag(t,"O"))
      case "BIO" => new ChainChunker[BIOChunkTag](BIOChunkDomain.dimensionDomain, (t) => new BIOChunkTag(t,"O"))
      //Nested NP Chunker has to be trained from custom training data annotated in the NestedBILOUChunkTag domain style
      case "NESTED" => new ChainChunker[BILOUNestedChunkTag](BILOUNestedChunkDomain.dimensionDomain, (t) => new BILOUNestedChunkTag(t,"O:O"))
    }

    val trainDocs = LoadConll2000.fromSource(Source.fromFile(opts.trainFile.value),opts.inputEncoding.value)
    val testDocs =  LoadConll2000.fromSource(Source.fromFile(opts.testFile.value),opts.inputEncoding.value)

    println("Read %d training tokens.".format(trainDocs.map(_.tokenCount).sum))
    println("Read %d testing tokens.".format(testDocs.map(_.tokenCount).sum))

    val trainPortionToTake = if(opts.trainPortion.wasInvoked) opts.trainPortion.value.toDouble  else 1.0
    val testPortionToTake =  if(opts.testPortion.wasInvoked) opts.testPortion.value.toDouble  else 1.0
    val trainSentencesFull = trainDocs.flatMap(_.sentences).filter(!_.isEmpty)
    val trainSentences = trainSentencesFull.take((trainPortionToTake*trainSentencesFull.length).floor.toInt)
    val testSentencesFull = testDocs.flatMap(_.sentences).filter(!_.isEmpty)
    val testSentences = testSentencesFull.take((testPortionToTake*testSentencesFull.length).floor.toInt)

    //If we want to load in BIO training data like conll2000, convert to BILOU encoding so BILOU training can be performed
    if(opts.trainingEncoding.value == "BILOU" && opts.inputEncoding.value =="BIO") {
      LoadConll2000.convertBIOtoBILOU(testSentences)
      LoadConll2000.convertBIOtoBILOU(trainSentences)
    }else{
      //Else make sure training encoding and input encoding match
      if(opts.trainingEncoding.value != opts.inputEncoding.value) throw new Exception("Specified Training Encoding: " + opts.trainingEncoding.value + " does not match Document Encoding: " + opts.inputEncoding.value)
    }

    chunk.train(trainSentences, testSentences, opts.useFullFeatures.value,
      opts.rate.value, opts.delta.value, opts.cutoff.value, opts.updateExamples.value, opts.useHingeLoss.value, l1Factor=opts.l1.value, l2Factor=opts.l2.value)
    if (opts.saveModel.value) {
      chunk.serialize(new FileOutputStream(new File(opts.modelFile.value)))
      println("Model Serialized")
    }
    val acc = HammingObjective.accuracy(testDocs.flatMap(d => d.sentences.flatMap(s => s.tokens.map(_.attr.all[ChunkTag].head))))
    if(opts.targetAccuracy.wasInvoked) assert(acc > opts.targetAccuracy.value.toDouble, "Did not reach accuracy requirement")
    if(opts.errorOutput.value) {
      val writer = new PrintWriter(new File("ChainChunkingOutput.txt" ))
      testSentences.foreach{s=>writer.write(generateErrorOutput(s)); writer.write("")}
      writer.close()
    }
    acc
  }
}


object ChainChunkerOptimizer {
  def main(args: Array[String]) {
    val opts = new ChunkerOpts
    opts.parse(args)
    opts.saveModel.setValue(false)
    val l1 = cc.factorie.util.HyperParameter(opts.l1, new cc.factorie.util.LogUniformDoubleSampler(1e-10, 1e2))
    val l2 = cc.factorie.util.HyperParameter(opts.l2, new cc.factorie.util.LogUniformDoubleSampler(1e-10, 1e2))
    val rate = cc.factorie.util.HyperParameter(opts.rate, new cc.factorie.util.LogUniformDoubleSampler(1e-4, 1e4))
    val delta = cc.factorie.util.HyperParameter(opts.delta, new cc.factorie.util.LogUniformDoubleSampler(1e-4, 1e4))
    val cutoff = cc.factorie.util.HyperParameter(opts.cutoff, new cc.factorie.util.SampleFromSeq(List(0,1,2,3)))
    val qs = new cc.factorie.util.QSubExecutor(60, "cc.factorie.app.nlp.chunk.ChainChunkingTrainer")
    val optimizer = new cc.factorie.util.HyperParameterSearcher(opts, Seq(l1, l2, rate, delta, cutoff), qs.execute, 200, 180, 60)
    val result = optimizer.optimize()
    println("Got results: " + result.mkString(" "))
    println("Best l1: " + opts.l1.value + " best l2: " + opts.l2.value)
    opts.saveModel.setValue(true)
    println("Running best configuration...")
    import scala.concurrent.duration._
    import scala.concurrent.Await
    Await.result(qs.execute(opts.values.flatMap(_.unParse).toArray), 5.hours)
    println("Done")
  }
}

class ChunkerOpts extends cc.factorie.util.DefaultCmdOptions with SharedNLPCmdOptions{
  val conllPath = new CmdOption("rcv1Path", "../../data/conll2000", "DIR", "Path to folder containing RCV1-v2 dataset.")
  val outputPath = new CmdOption("ouputPath", "../../data/conll2000/output.txt", "FILE", "Path to write output for evaluation.")
  val modelFile = new CmdOption("model", "ChainChunker.factorie", "FILENAME", "Filename for the model (saving a trained model or reading a running model.")
  val testFile = new CmdOption("test", "src/main/resources/test.txt", "FILENAME", "test file.")
  val trainFile = new CmdOption("train", "src/main/resources/train.txt", "FILENAME", "training file.")
  val l1 = new CmdOption("l1", 0.000001,"FLOAT","l1 regularization weight")
  val l2 = new CmdOption("l2", 0.00001,"FLOAT","l2 regularization weight")
  val rate = new CmdOption("rate", 10.0,"FLOAT","base learning rate")
  val delta = new CmdOption("delta", 100.0,"FLOAT","learning rate decay")
  val cutoff = new CmdOption("cutoff", 2, "INT", "Discard features less frequent than this before training.")
  val updateExamples = new  CmdOption("update-examples", true, "BOOL", "Whether to update examples in later iterations during training.")
  val useHingeLoss = new CmdOption("use-hinge-loss", false, "BOOL", "Whether to use hinge loss (or log loss) during training.")
  val saveModel = new CmdOption("save-model", false, "BOOL", "Whether to save the trained model.")
  val runText = new CmdOption("run", "", "FILENAME", "Plain text file on which to run.")
  val numIters = new CmdOption("num-iterations","5","INT","number of passes over the data for training")
  val inputEncoding = new CmdOption("input-encoding","BIO","String","NESTED, BIO, BILOU - Encoding file used for training is in.")
  val trainingEncoding = new CmdOption("train-encoding", "BILOU","String","NESTED, BIO, BILOU - labels to use during training.")
  val useFullFeatures = new CmdOption("full-features", false,"BOOL", "True to use the full feature set, False to use a smaller feature set which is the default.")
  val errorOutput = new CmdOption("print-output", false,"BOOL", "True to print output to file for error analysis and debugging purposes.")

}
