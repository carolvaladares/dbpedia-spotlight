package org.dbpedia.spotlight.corpus

import org.dbpedia.spotlight.io.AnnotatedTextSource
import org.dbpedia.spotlight.model._

import io.Source

import java.io._

import collection.mutable.HashMap

import org.xml.sax.InputSource

import opennlp.tools.tokenize.{TokenizerModel, TokenizerME, Tokenizer}
import opennlp.tools.sentdetect.{SentenceModel, SentenceDetectorME, SentenceDetector}

/*import org.dbpedia.spotlight.spot.factorie.LinearChainCRFSpotter
import org.dbpedia.spotlight.model.Factory.OntologyType
*/

/**
 * Occurrence source for reading Brat corpus.
 *
 * @author Carol Valadares
 */


/**
 * @param id Annotation ID. Ex: T6, T79
 * @param entity Annotation type: PERSON, LOCATION or ORGANISATION.
 * @param begin Annotation  offset begin
 * @param end Annotation offset end
 * @param sf Annotation, which will be converted to SurfaceForm
 * @param article Globo.com article URL
 */
case class Brat(var id: String, var entity: String, var begin: Int, var end: Int, var sf: String, var article: String)


/**
 * This class reads a Brat Corpus as AnnotatedTextSource
 *
 * @param texts The annotated articles stored by the file name as the Map key.
 * @param annotations The annotation file stored by the file name as the MAp Key.
 * ie. texts(i) stores the text itself while anotations(i) its corresponding annotation.
 */
class BratCorpus(val texts: HashMap[String, String], val annotations: HashMap[String, String]) extends AnnotatedTextSource {

  override def name = "Brat"

  override def foreach[U](f : AnnotatedParagraph => U) {
    val occs  = HashMap[String, List[DBpediaResourceOccurrence]]()

    /** For each annotation of key KEY*/
    annotations.keys foreach (key => {

      /** Get its corresponding text on key KEY */
      var brat = annotations(key).split("\\r?\\n") map( line => {

      /** Grab annotation information 
          (id \t type begin end \t annotation)  **/
      var ann = line.split("\t") 
        if(ann.size > 2) {
          var offsets = ann(1).split(" ")
          // Filter annotations by type:
          //if(offsets(0).equals("Location") || offsets(0).equals("Place"))
          new Brat(ann(0), offsets(0), offsets(1).toInt, offsets(2).toInt, ann(2), key)
        }
      })

      /** Sort annotations  by offset begin */
      var annotationSorted = brat.filter(a => a.isInstanceOf [Brat] ).sortBy(a => a.asInstanceOf[Brat].end)

      /** Convert  Annotations to DBpediaResourceOccurrences */
      val occs = annotationSorted.map{ case (occ:Brat) => {
                new DBpediaResourceOccurrence(occ.article + "-" + occ.begin,
                    new DBpediaResource(occ.sf) ,
                    new SurfaceForm(occ.sf),
                    new Text(texts(key)),
                    occ.begin,
                    Provenance.Manual,
                    1.0)
            }}.toList

      val annotated = new AnnotatedParagraph(key, new Text(texts(key)), occs)
      f(annotated)

    })
  }
}

/**
 * This class reads a Brat Corpus and convert it to OpenNLO format.
 *
 * @param texts The annotated articles stored by the file name as the Map key.
 * @param annotations The annotation file stored by the file name as the MAp Key.
 * ie. texts(i) stores the text itself while anotations(i) its corresponding annotation.
 */
class ConvertFromBratToOpenNLPCorpus(val texts: HashMap[String, String], val annotations: HashMap[String, String]) extends AnnotatedTextSource {

  /** OpenNLO sentence model */
  val sentenceDetector: SentenceDetector = new SentenceDetectorME(new SentenceModel(new FileInputStream("/mnt/dbpediaSpotlight/spotlight/data/opennlp/pt/pt-sent.bin")))

  /** !!Durty!! fix for sentence braking, as the code breaks an setence in two when it finds an <START> tag */
  def fix(a: String) =  """\.(\s*)<START:""".r.replaceAllIn(a, ".\n<START:")
  
  /** !!Durty adn quick!! Tokenizing of an article */
  def tokenize(corpus: List[String]) : List[String] = 
    corpus.map(x => 
        sentenceDetector.sentDetect(x.replaceAll("\\."," \\. ").replaceAll("\\,"," \\, ").replaceAll("\\?"," \\? ").replaceAll("\\!"," \\! ")
                 .replaceAll("\\("," \\( ").replaceAll("\\)"," \\) ").replaceAll("\\]"," \\] ").replaceAll("\\["," \\[ ") 
                 .replaceAll("\""," \" ").replaceAll("'" , " ' ")
        ) .toList
    ).flatten

  /** Add a  Annotation Tag to the Corpus: <STRAR:entity> Annotation <END>
  *
  * @param content is the extracted (OpenNLP) corpus
  * @param offsetBegin  is the annotation begin offset 
  * @param offsetEnd is the annotation end offset
  * @param extra is the ofsset fixing 
  * @param entity is the annotation type.
  **/
  def addTag(content: StringBuilder, offsetBegin : Int, offsetEnd: Int, extra: Int, entity: String) : Int = {
    var count = extra

    content.insert( offsetBegin + count, " <START:" + entity + "> ") 
    count = count + (" <START:" + entity + "> ").size
    content.insert( offsetEnd  +  count, " <END> ") 
    count = count + (" <END> ").size

    count
  }
  
  /** Converts the corpus and generates 4 files: 
   *
   * *.all.all PER, LOC and ORG annotations of all articles
   * *.all.per only PER annotation of all articles
   * *.all.pla only PLA annotation of all articles
   * *.all.org only ORG annotation of all articles
   **/
   override def foreach[U](f : AnnotatedParagraph => U) {
    
    var cospus = List[String]()
    var cospusPer = List[String]()
    var cospusPla = List[String]()
    var cospusOrg = List[String]()

    val all = new PrintStream(  "globo.ner.all.all" , "UTF-8")
    val per = new PrintStream(  "globo.ner.all.per" , "UTF-8")
    val pla = new PrintStream(  "globo.ner.all.pla" , "UTF-8")
    val org = new PrintStream(  "globo.ner.all.org" , "UTF-8")

    annotations.keys foreach (key => {

      var ANNall = new StringBuilder( texts(key) )
      var ANNper = new StringBuilder( texts(key) )
      var ANNpla = new StringBuilder( texts(key) )
      var ANNorg = new StringBuilder( texts(key) )

      var extraall = 0
      var extraper = 0
      var extrapla = 0
      var extraorg = 0

      /** Breaking  annotations by article */
      var annotation = annotations(key).split("\\r?\\n")

      /** Extracting the annotartion info from Brat Files */
      var test = annotations(key).split("\\r?\\n") map( line => {
        var ann = line.split("\t") 
         if(ann.size > 2) {
          var offsets = ann(1).split(" ")
          new Brat(ann(0), offsets(0), offsets(1).toInt, offsets(2).toInt, ann(2), key)
         } 
       })

      /** Sorting annotations by offset begin*/
      var annotationSorted = test.filter(a => a.isInstanceOf [Brat] ).sortBy(a => a.asInstanceOf[Brat].end)

      annotationSorted foreach( a => {
        var line = a.asInstanceOf[Brat]

          if(line.id != null && line.sf != null && !line.sf.equals("")){

              if(line.entity.equals("Person")){
                extraper = addTag(ANNper, line.begin, line.end, extraper, "person")
                extraall = addTag(ANNall, line.begin, line.end, extraall, "person")
              }
              else if (line.entity.equals("Organisation")){
                extraorg = addTag(ANNorg, line.begin, line.end, extraorg, "organization")
                extraall = addTag(ANNall, line.begin, line.end, extraall, "organization")
              }
              else if (line.entity.equals("Location") || line.entity.equals("Place")){
                extrapla = addTag(ANNpla, line.begin, line.end, extrapla, "place")
                extraall = addTag(ANNall, line.begin, line.end, extraall, "place")
              }
          }
      })

      cospus =       cospus :+ ANNall.toString.replaceAll("\n\n", "\n")
      cospusPer = cospusPer :+ ANNper.toString.replaceAll("\n\n", "\n")
      cospusPla = cospusPla :+ ANNpla.toString.replaceAll("\n\n", "\n")
      cospusOrg = cospusOrg :+ ANNorg.toString.replaceAll("\n\n", "\n")

    })

    /** Prints the corpus to the disk */
    all.println( fix(tokenize(cospus).mkString("\n")) )
    per.println( fix(tokenize(cospusPer).mkString("\n")) )
    pla.println( fix(tokenize(cospusPla).mkString("\n")) )
    org.println( fix(tokenize(cospusOrg).mkString("\n") ))

  }
}

/**
 * Reads the Brat Files, which are:
 *
 * There are different type os files to build a Brat Corpus, see the parameters below:
 *
 * file.ann, stores all annotations of an article
 * file.txt, stores the article text itself, without any annotation information.
 * both Maps contains the same keys to map between text and annotations.
 */
object BratCorpus {

  def fromDirectory(directory: File, folder: String) : ( HashMap[String, String], HashMap[String, String]) = {

    val texts = HashMap[String, String]()
    val ann = HashMap[String, String]()

    /** Reads annotations */
    new File(directory, folder).listFiles.filter(f => (f.getName.contains(".ann")  /*&& f.getName.contains("loc_")*/ ) && 
      !f.getName.contains(".place.") && !f.getName.contains(".person.") && !f.getName.contains(".organisation.")   ) foreach(
      crawledDoc => {
        var id = crawledDoc.getName.dropRight(4)
        ann.put(id, (Source.fromFile(crawledDoc).mkString))
      }
    )
    /** Reads text*/
     new File(directory, folder).listFiles.filter(f => (f.getName.contains(".txt") )  ) foreach(
      crawledDoc => {
        var id = crawledDoc.getName.dropRight(4)
        texts.put(id, (Source.fromFile(crawledDoc).mkString))
      }
    )

     (texts, ann)
    //new BratCorpus(texts, ann)
    //new ConvertFromBratToOpenNLPCorpus(texts, ann)
  }

  /** Converto to Dbpedia AnnotatedTextSource*/
  def fileToDbpediaFormat(directory: File, folder: String) : AnnotatedTextSource = {
      var (texts, ann) = fromDirectory(directory, folder) 
      new BratCorpus(texts, ann)
  }

  /** Convert to OpenNLO AnnotatedTextSource - stored at disk **/
  def fileToOpenNLPFormat(directory: File, folder: String) : AnnotatedTextSource  = {
      var (texts, ann) = fromDirectory(directory, folder) 
      new ConvertFromBratToOpenNLPCorpus(texts, ann)
  }

  def main (args: Array[String]) {
    val dir = new File("/home/annotate/public_html/brat/data/Globo/")
    
    if( args.size > 0 && args(0).toLowerCase.contains("opennlp")){
      
      var brat = BratCorpus.fileToOpenNLPFormat(dir, "ann")
      .foreach( p => {
        println(p)
      })
    
    }
    else {

      var brat = BratCorpus.fileToDbpediaFormat(dir, "ann")
      .foreach( p => {
        println(p)
      })      
    }
  }
}

