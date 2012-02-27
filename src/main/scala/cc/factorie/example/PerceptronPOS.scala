package cc.factorie.example

import cc.factorie._
import cc.factorie.bp.optimized._
import cc.factorie.app.nlp._
import cc.factorie.app.chain.Observations.addNeighboringFeatureConjunctions
import pos.{PosLabel, PosFeatures, PosDomain, PosFeaturesDomain}

/**
 * Author: martin
 * Date: 2/8/12
 *
 * A simple chain POS tagger trained using optimized Viterbi inference
 * and averaged, structured perceptron learning.
 */

object PerceptronPOS {

  object PosModel extends TemplateModel {
    // Factor between label and observed token
    val localTemplate = new TemplateWithDotStatistics2[PosLabel,PosFeatures] {
      override def statisticsDomains = Seq(PosDomain, PosFeaturesDomain)
      def unroll1(label: PosLabel) = Factor(label, label.token.attr[PosFeatures])
      def unroll2(tf: PosFeatures) = Factor(tf.token.posLabel, tf)
    }
    // Transition factors between two successive labels
    val transTemplate = new TemplateWithDotStatistics2[PosLabel, PosLabel] {
      override def statisticsDomains = Seq(PosDomain, PosFeaturesDomain)
      def unroll1(label: PosLabel) = if (label.token.sentenceHasPrev) Factor(label.token.sentencePrev.posLabel, label) else Nil
      def unroll2(label: PosLabel) = if (label.token.sentenceHasNext) Factor(label, label.token.sentenceNext.posLabel) else Nil
    }

    this += localTemplate
    this += transTemplate
  }

  def initPosFeatures(documents: Seq[Document]): Unit = documents.map(initPosFeatures(_))
  def initPosFeatures(document: Document): Unit = {
    for (token <- document) {
      val rawWord = token.string
      val word = cc.factorie.app.strings.simplifyDigits(rawWord)
      val features = new PosFeatures(token)
      // val features = token.attr += new PosFeatures(token)
      token.attr += features
      features += "W=" + word
      features += "SHAPE3=" + cc.factorie.app.strings.stringShape(rawWord, 3)
      val i = 3
      features += "SUFFIX" + i + "=" + word.takeRight(i)
      features += "PREFIX" + i + "=" + word.take(i)
      if (token.isCapitalized) features += "CAPITALIZED"
      if (token.string.matches("[A-Z]")) features += "CONTAINS_CAPITAL"
      if (token.string.matches("-")) features += "CONTAINS_DASH"
      if (token.containsDigit) features += "NUMERIC"
      if (token.isPunctuation) features += "PUNCTUATION"
    }
    for (sentence <- document.sentences)
      addNeighboringFeatureConjunctions(sentence, (t: Token) => t.attr[PosFeatures], "W=", List(-2), List(-1), List(1), List(-2,-1), List(-1,0))
  }

  def percentageSetToTarget[L <: VarWithTarget](ls: Seq[L]): Double = {
    val numCorrect = ls.foldLeft(0.0)((partialSum, label) => partialSum + {if (label.valueIsTarget) 1 else 0})
    numCorrect / ls.size * 100
  }

  val searcher = new BeamSearch with FullBeam

  def predictSentence(s: Sentence): Unit = predictSentence(s.map(_.posLabel))
  def predictSentence(vs: Seq[PosLabel], oldBp: Boolean = false): Unit =
    searcher.searchAndSetToMax(PosModel.localTemplate, PosModel.transTemplate, vs)

  def train(
        documents: Seq[Document],
        devDocuments: Seq[Document],
        testDocuments: Seq[Document] = Seq.empty[Document],
        iterations: Int = 100,
        modelFile: String = "",
        extraId: String = ""): Unit = {

    def testSavePrint(label: String): Unit = {
      test(documents, label = "train")
      if (testDocuments.nonEmpty)
        test(testDocuments, label = "test")
      if (devDocuments.nonEmpty)
        test(devDocuments, label = "dev")
      if (modelFile != "")
        PosModel.save(modelFile + label + extraId)
    }

    val sentenceLabels: Array[Seq[PosLabel]] = documents.flatMap(_.sentences).map(_.posLabels).filter(_.size > 0).toArray
    val learner = new AveragedStructuredPerceptron[PosLabel] {
      val model = PosModel
      def predict(vs: Seq[PosLabel]) = predictSentence(vs)
    }

    for (i <- 1 to iterations) {
      println("Training iteration: " + i)
      var start = System.currentTimeMillis()
      var j = 0
      while (j < sentenceLabels.size) {
        learner.process(sentenceLabels(j))
        j += 1
      }
      println("Done in " + (System.currentTimeMillis()-start)/1000.0 + "s\n")

      println("Testing with unaveraged weights...")
      start = System.currentTimeMillis()
      testSavePrint("iteration=" + i)
      println("Done in " + (System.currentTimeMillis()-start)/1000.0 + "s\n")

      learner.setToAveraged()
      println("Testing with averaged weights...")
      start = System.currentTimeMillis()
      testSavePrint("iteration=" + i + "-averaged")
      println("Done in " + (System.currentTimeMillis()-start)/1000.0 + "s\n")
      println("-----------------------")

      learner.unsetAveraged()
    }

    /* The following is an example of how to use Stochastic Gradent updates with Perceptron gradients. */
    /*
    val pieces = sentenceLabels.map(vs => new PerceptronChainPiece(SGDPosModel.localTemplate, SGDPosModel.transTemplate, vs.toArray))
    val SGDLearner = new SGDTrainer(pieces, SGDPosModel.familiesOfClass[DotFamily], 50, 0.01, 1, 1.0, 0.0)
    for (i <- 0 until iterations) {
      val t = System.currentTimeMillis()
      SGDLearner.iterate()
      println("Time per iter: "+(System.currentTimeMillis() - t)/1000.0)
      val t2 = System.currentTimeMillis()
      testSavePrint("foo")
      println("Time per evaluation: "+(System.currentTimeMillis()-t2)/1000.0)
    }
    */
  }

  def test(documents: Seq[Document], label: String = "test"): Unit = {
    val sentences = documents.flatMap(_.sentences)
    val labels = sentences.flatMap(_.map(t => t.posLabel))
    labels.map(_.setRandomly())
    sentences.map(predictSentence(_))
    println(label + " accuracy: " + percentageSetToTarget(labels) + "%")
  }

  var modelLoaded = false
  def load(modelFile: String) = { PosModel.load(modelFile); modelLoaded = true }

  def process(documents: Seq[Document]): Unit = documents.map(process(_))
  def process(document: Document): Unit = {
    if (!modelLoaded) throw new Error("The model should be loaded before documents are processed.")

    // add the labels and features if they aren't there already.
    if (document.head.attr.get[PosLabel] == None) {
      document.foreach(t => t.attr += labelMaker(t))
      initPosFeatures(document)
    }

    document.sentences.foreach(predictSentence(_))
  }

  lazy val defaultCategory = {
    try { PosDomain.categoryValues.head }
    catch { case e: NoSuchElementException => throw new Error("The domain must be loaded before it is accessed.") }
  }
  def labelMaker(t: Token, l: String = defaultCategory) = new PosLabel(t, l)

  def main(args: Array[String]): Unit = {
    object opts extends cc.factorie.util.DefaultCmdOptions {
      val trainFile = new CmdOption("train", "", "FILE", "An OWPL train file.") { override def required = true }
      val devFile =   new CmdOption("dev", "", "FILE", "An OWPL dev file") { override def required = true }
      val testFile =  new CmdOption("test", "", "FILE", "An OWPL test file.") { override def required = true }
      val takeOnly =  new CmdOption("takeOnly", "-1", "INT", "A limit on the number of sentences loaded from each file.")
      val iterations =new CmdOption("iterations", "10", "INT", "The number of iterations to train for.")
      val modelDir =  new CmdOption("model", "", "DIR", "Directory in which to save the trained model.")
      val extraId =   new CmdOption("label", "", "STRING", "An extra identifier.  Useful for testing different sets of features.")
    }
    opts.parse(args)
    import opts._

    if (trainFile.wasInvoked && devFile.wasInvoked && testFile.wasInvoked) {
      def load(f: String): Seq[Document] = LoadOWPL.fromFilename(f, labelMaker, takeOnly.value.toInt)

      // load the data
      val trainDocs = load(trainFile.value)
      val devDocs = load(devFile.value)
      val testDocs = load(testFile.value)
      println("train sentences: " + trainDocs.flatMap(_.sentences).size)
      println("dev   sentences: " + devDocs.flatMap(_.sentences).size)
      println("test  sentences: " + testDocs.flatMap(_.sentences).size)
      println("docs loaded")

      // calculate features
      initPosFeatures(trainDocs ++ devDocs ++ testDocs)
      println("train, dev, and test features calculated")

      train(trainDocs, devDocs, testDocs,
        iterations = iterations.value.toInt,
        modelFile = modelDir.value,
        extraId = extraId.value
      )
    }
  }

}