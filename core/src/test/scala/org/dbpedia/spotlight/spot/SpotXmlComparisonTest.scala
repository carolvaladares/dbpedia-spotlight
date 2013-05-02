package org.dbpedia.spotlight.spot
//package org.kodejava.example.commons.io

//import org.apache.commons.io.FileUtils
//import java.io.File
//import java.io._
//import java.io.IOException

import scala.io.Source._

import xml.{Node, XML}
import org.dbpedia.spotlight.model.{SurfaceForm, SurfaceFormOccurrence, Text}
import scala.collection.JavaConversions._

/**
 * Created with IntelliJ IDEA.
 * User: Renan
 * Date: 01/05/13
 * Time: 13:45
 * To change this template use File | Settings | File Templates.
 */
class SpotXmlComparisonTest extends Spotter { //extends FlatSpec with ShouldMatchers {
  var name = "SpotXmlComparisonTest"

  /**
   * Extracts a set of surface form occurrences from the text
   */
  def extract(spotsXml: Text): java.util.List[SurfaceFormOccurrence] = {
    val xml = XML.loadString(spotsXml.text)
    val text = (xml \\ "annotation" \ "@text").toString
    val surfaceForms = xml \\"annotation" \ "surfaceForm"
    val occs = surfaceForms.map(buildOcc(_, new Text(text)))
    occs.toList
  }

  def buildOcc(sf: Node, text: Text) = {
    val offset = (sf \ "@offset").toString.toInt
    val name = (sf \ "@name").toString
    new SurfaceFormOccurrence(new SurfaceForm(name), text, offset)
  }

  def getName() = name

  def setName(n: String) {
    name = n;
  }
}

object SpotXmlComparisonTest {
  def main(args: Array[String]) {
    //File file = new File("C:\\Users\\Renan\\Documents\\GitHub\\dbpedia-spotlight\\core\\src\\test\\resources\\annotation1.xml");

    val xml = scala.io.Source.fromFile("C:\\Users\\Renan\\Documents\\GitHub\\dbpedia-spotlight\\core\\src\\test\\resources\\annotation1.xml", "utf-8").mkString

    //val xml = "<annotation text=\"The research, which is published online May 22 in the European Heart Journal, opens up the prospect of treating heart failure patients with their own, human-induced pluripotent stem cells (hiPSCs) to repair their damaged hearts.\">\n<surfaceForm name=\"published\" offset=\"23\"/>\n<surfaceForm name=\"May 22\" offset=\"40\"/>\n<surfaceForm name=\"European\" offset=\"54\"/>\n<surfaceForm name=\"Heart\" offset=\"63\"/>\n<surfaceForm name=\"Journal\" offset=\"69\"/>\n<surfaceForm name=\"prospect\" offset=\"91\"/>\n<surfaceForm name=\"heart failure\" offset=\"112\"/>\n<surfaceForm name=\"patients\" offset=\"126\"/>\n<surfaceForm name=\"human\" offset=\"151\"/>\n<surfaceForm name=\"stem cells\" offset=\"177\"/>\n<surfaceForm name=\"hearts\" offset=\"221\"/>\n</annotation>"
    //val xml = FileUtils.readFileToString(file, "UTF-8");
    val spotter = new SpotXmlComparisonTest()
    spotter.extract(new Text(xml)).foreach(println)
  }
}