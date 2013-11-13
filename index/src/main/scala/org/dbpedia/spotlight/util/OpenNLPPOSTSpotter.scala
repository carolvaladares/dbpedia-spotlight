package org.dbpedia.spotlight.util

import scala.collection.JavaConverters._
import org.dbpedia.spotlight.spot.{OpenNLPUtil, Spotter}
import opennlp.tools.chunker.{ChunkerModel, ChunkerME, Chunker}
import java.io.FileInputStream
import java.io.File
import opennlp.tools.postag.{POSModel, POSTaggerME, POSTagger}
import opennlp.tools.tokenize.{TokenizerModel, TokenizerME, Tokenizer}
import opennlp.tools.sentdetect.{SentenceModel, SentenceDetectorME, SentenceDetector}
import java.util.LinkedList
import scala.util.control.Breaks._
import org.dbpedia.spotlight.model.{SurfaceForm, SurfaceFormOccurrence, Text}
import collection.mutable.HashSet
import io.Source
import scala.collection.mutable.ListBuffer



/**
 * OpenNLP based Spotter performing VP chunking and extracting a number of verbs related to a specicf surfaceForm from a wikipedia Dump.
 * The number of extracted verbs is definid by the variable @window.
 * We currently can extract 3 types of verbs files from a wikipedia dump: Left-verbs, Right-verbs and all verbs.
 *
 * @author carolvaladares
 */

class OpenNLPPOSTSpotter(
  sentenceModel: File,
  tokenizerModel: File,
  posModel: File,
  chunkerModel: File, 
  window: Int = 6
)  {

  val posTagger: POSTagger = new POSTaggerME(new POSModel(new FileInputStream(posModel)))

  val sentenceDetector: SentenceDetector = new SentenceDetectorME(new SentenceModel(new FileInputStream(sentenceModel)))

  val tokenizer: Tokenizer = new TokenizerME(new TokenizerModel(new FileInputStream(tokenizerModel)))

  val chunker: Chunker = new ChunkerME(new ChunkerModel(new FileInputStream(chunkerModel)))

  def extract(text: Text, subject : String): Array[ListBuffer[String]] = {
    
    val sub = subject.replaceAll(" ", "_")
    val tex = text.text.replaceAll(subject, sub)
    
    val spots = new ListBuffer[String]()
    //0 right, 1 left
    var results = Array(new ListBuffer[String](), new ListBuffer[String]())
    val sentences = sentenceDetector.sentPosDetect(tex)

    //sentences.foreach(tag =>{print(text.text.substring(tag.getStart, tag.getEnd))})

    //Go through all sentences
    sentences.foreach(sentencePosition => {
    
      val sentence = tex.substring(sentencePosition.getStart, sentencePosition.getEnd)
      var tokens = tokenizer.tokenize(sentence);
      var tokensPositions = tokenizer.tokenizePos(sentence);
      var tags = posTagger.tag(tokens)
      
      var start: Int = 0
      var end : Int = 0
      var postag: Int = 0
      var i: Int = 0
     
       (tokens,tokensPositions).zipped.foreach((tag, pos) =>{ 
       
         if(sub.equals(tag.trim())){
             postag = i
             start = if(i - window <= 0 ) 0 else i - window 
             end =  if(i + window >= tokens.size ) tokens.size  else i + window
          }
          i = i + 1
       })
       
      //tokens.foreach(tag =>{ if(subject.contains(sentence.substring(tag.getStart, tag.getEnd))) println(">> " + tag)})
      // tags.foreach(tag =>{sentence.substring(tag.getStart, tag.getEnd) + "  "})

      //Go through all chunks
       
       tokens = tokens.slice(start, end)
       tokensPositions = tokensPositions.slice(start, end)
       tags = tags.slice(start, end)
       
      chunker.chunkAsSpans(tokens, tags)

        //Only look at NPs
        .filter(chunkSpan => chunkSpan.getType.startsWith("VP"))
        .foreach(chunkSpan => {
         
        //  breakable {
            val firstToken = chunkSpan.getStart 
            val lastToken = chunkSpan.getEnd-1

            (firstToken to lastToken).foreach(startToken => {
              val startOffset: Int = tokensPositions(startToken).getStart
              val endOffset: Int = tokensPositions(lastToken).getEnd
              val spot = sentence.substring(startOffset, endOffset)

              if( startOffset <= i)
              	results(0)  +=   spot
              else 
              	results(1)  +=  spot      
            })
          //}
      })
    })

    results
  }

}

object OpenNLPPOSTSpotter {

    
    def main(args : Array[String]) {
      
    	val post = new OpenNLPPOSTSpotter( 
    	    new File("/mnt/dbpediaSpotlight/spotlight/data/opennlp/en/en-sent.bin"),
    	    new File("/mnt/dbpediaSpotlight/spotlight/data/opennlp/en/en-token.bin"),
    	    new File("/mnt/dbpediaSpotlight/spotlight/data/opennlp/en/en-pos-maxent.bin"),
    	    new File("/mnt/dbpediaSpotlight/spotlight/data/opennlp/en/en-chunker.bin") 
    	    )
    	
    	
    	val spots = post.extract(new Text("""As a subtle and anti-dogmatic philosophy, anarchism draws on many currents of thought and strategy. Anarchism does not offer a fixed body 
of doctrine from a single particular world view, instead fluxing and flowing as a philosophy. There are many types and traditions of anarchism, not all of which are mutually exclusive. Anarchist schools
 of thought can differ fundamentally, supporting anything from extreme individualism to complete collectivism. Anarchism is often considered a radical left-wing ideology, and much of anarchist economics
 and anarchist legal philosophy reflect anti-authoritarian eat, drink, having fun, interpretations of communism, collectivism, syndicalism, mutualism, or participatory economics"""),  "participatory economics")
    	
    //	println(spots.toString) 	
    }

}
