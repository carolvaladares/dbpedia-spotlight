/* Copyright 2012 Intrinsic Ltda.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
* Check our project website for information on how to acknowledge the
* authors and how to contribute to the project:
* http://spotlight.dbpedia.org
*/

package org.dbpedia.spotlight.util

import java.io.{IOException, FileInputStream, InputStream, PrintStream}
import scala.io.Source
import scala.Option.option2Iterable
import com.hp.hpl.jena.rdf.model.Model
import com.typesafe.config.{ConfigFactory, Config}
import java.util.Properties
import org.dbpedia.spotlight.exceptions.ConfigurationException

/**
 * Context Extraction from RDF
 * Used to process the properties and labels and group them by the subject
 * The output is formatted as TSV or JSON
 *
 * @author reinaldo
 */
object RDFContextExtractor  { //extends App

 // val config = ConfigFactory.load;
  
  /**
   * Choose the format of the output
   * Choose the relevant part to be extracted: object, property or type
   * Fill the input and output file names
   * Check the output to see the extraction
   */

  /** Use it to create databases at filesystem*/
  /** DataConn.create_TDB_Filesystem(
   *  config.getString("dataSet.location"), 
   *  config.getString("dataSet.inputFile"));*/

  
  /** Get file extension */
  def extensor(file: String) = file.split('.').drop(1).lastOption
  
  /** Loads a datafile into the TDB DataConn.dataset, 
  * For the Context Extraction purpose, is must be the Labels DBpedia dataset.
  **/
  def loadDataSet(tdbFile: String, tdbLocation: String, tdbFormat: String) {
    /** Convert TDB input file into InputStream*/
    val tdbModel: InputStream = //if (extensor(tdbFile).equals(Some("bz2"))) 
    BZipUntar.convert(tdbFile) 
   	  					        //else BZipUntar.toInputStream(tdbFile)
    /** Creates and populates TDB */
    DataConn.createTDBFilesystem(tdbLocation, tdbModel, tdbFormat)
  }
  
  /** 
   * Extractor for windows and unix. It takes the dataset files to extract them.
   * Recommended for SMALL datasets, for larger sets, refer to @extract2 (Unix only)
   * args:
   * 
   * - loadtdb: represents whether it is necessary to reload the main tdb: DataConn.dataSet (Labels)
   * - tdbFile: main tdb input File
   * - tdbLocation: main tdb destination (in which it will be stored)
   * - tdbFormat: main tbb format, default (config file) is "RDF/XML"
   * - modelFile: model input file (the dataset to be extracted from)
   * - modelFormat: model format, default is "N-TRIPLE"
   * - extraction: extraction part - object or property.
   * - outformat: output format -  TSV or JSON or OCC
   * - outputFile: result file
   * - namedModel: model name
   */
  def extract(loadtdb: Boolean, 
		  	    tdbFile: String, 
		  	    tdbLocation: String, 
		  	    tdbFormat: String,
		  	    modelFile:String, 
		  	    modelFormat:String, 
		  	    extraction: String, 
		  	    outformat: String, 
		  	    outputFile: String, 
		  	    namedModel: String) {
    
    /** load for the first time or reload the DataConn.dataSet TDB if requested */
    if (loadtdb)
      loadDataSet(tdbFile, tdbLocation, tdbFormat)
   
    /** converts Model input file into InputStream **/
    val input: InputStream = ///if (extensor(modelFile).equals(Some("bz2")))
     BZipUntar.convert(modelFile) 
    						  // else BZipUntar.toInputStream(modelFile)
    		
    /** Get the main TDB - Labels dataset */
    DataConn.getTDBFilesystem(tdbLocation)
    
    /** Add Model (dataset to be extracted from )into main TDB as a namedModel **/
   DataConn.addModelToTDB(namedModel, input, modelFormat)
    
    /**Execute the extraction itself */
    RDFContextExtractor.labelExtraction(
      extraction,
      outformat,
      DataConn.dataSet.getNamedModel(namedModel),
      outputFile)
     
    /** Close tdb **/
    DataConn.dataSet.close()
  }
  
   /** 
   * Extractor for ** Unix only ** by using Jena tdbloader2. It takes the dataset files to extract them.
   * args:
   * 
   * - loadtdb: represents whether it is necessary to reload the main tdb: DataConn.dataSet
   * - tdbFile: main tdb input File - usually labels.nt
   * - tdbLocation: main tdb location - labels.nt
   * - tdbFormat: main tbb format, default (config file) is "RDF/XML" - labels.nt
   * - modelFile: model input file
   * - modelFormat: model format, default is "N-TRIPLE"
   * - extraction: extraction part - object or property.
   * - outformat: output format -  TSV or JSON
   * - outputFile: result file
   * - namedModelLocation: model tdb location
   * - datasetsLocation: directory when the downloaded dataset will be stored
   */
  def extract2(loadtdb: Boolean, 
		  	    tdbFile: String, 
		  	    tdbLocation: String, 
		  	    tdbFormat: String,
		  	    modelFile:String, 
		  	    modelFormat:String, 
		  	    extraction: String, 
		  	    outformat: String, 
		  	    outputFile: String, 
		  	    namedModelLocation: String,
		  	    datasetsLocation: String) {
    
    /** reload the DataConn.dataSet TDB if requested */
    if (loadtdb) {
      println("Loading Main TDB")
      loadDataSet(tdbFile, tdbLocation, tdbFormat)
    }
    
    /** Get the main TDB */
    DataConn.getTDBFilesystem(tdbLocation)
    
    println("Loading  " + modelFile + "TDBloader2")
    
    /*** Load TDB by using tdbloader2 - for Linux only **/
    LinuxTDBLoader.tdbloader2(modelFile, datasetsLocation, namedModelLocation)

    println("Getting Model " + namedModelLocation)
    /** Add Model into separated TDBs **/
    val model: Model = DataConn.getTDBFilesystemDataset(namedModelLocation).getDefaultModel
   
    println("Extracting")
    /**Execute the extraction itself */
    RDFContextExtractor.labelExtraction(
      extraction,
      outformat,
      model,
      outputFile)
      
    /** close tdbs used **/
    model.close()
    DataConn.dataSet.close()
  }
  
  
  /**
   * Reads in files into a Jena Model.
   * Performs context extraction, formatting and outputs context into a file.
   * Context extraction can focus on property labels, object labels or object type labels.
   */
  def labelExtraction(partToBeExtracted: String, format: String, namedModel: Model, outputFile: String) {
    
    /** creating the output*/
    val output = new PrintStream(outputFile)
    
    /** choosing output format*/
    val formatter = if (format.equals("TSV")) new TSVOutputFormatter 
    				else if (format.equals("OCC")) new OCCOutputFormatter
    				else new PigOutputFormatter
    
    /** choosing extraction strategy*/
    val extractor = if (partToBeExtracted.equals("object")) new ObjectExtractor else new PropertyExtractor
    
    /** applying over input*/
    var source: JenaStatementSource =  new JenaStatementSource( namedModel)
    
    source.groupBy(e => e.getSubject).flatMap {
      case (subject, statements) => {
        val context = extractor.extract(statements).mkString(" ")
        output.println(formatter.format(subject.getLocalName, context))
        Some((subject, context))
      }
      case _ => None
    }.toSeq
     .sortBy(e => e._1.getLocalName)
   
    output.close()
    source = null  
  }
  
  /***
   * Extraction Strings formating
   *
   * example of usage:
   * 
   * input:
   * http://downloads.dbpedia.org/3.8/pt/page_links_pt.nt.bz2
   * 
   * output:
   * ["files/outputs/page_links.pt.nt.obj.tsv",   "http://downloads.dbpedia.org/3.8/pt/page_links_pt.nt.bz2",   "page_links_pt_nt"]
   */
  def getFiles(httpFile: String, input: String, outputDir: String) : Array[String] = {
    
    /** file full name. ex: page_links_pt.nt.bz2 */
    val last: String = httpFile.split("/").last 
    /** file name with language. ex: page_links_pt*/
    val nameLang: String = last.replace("." + input + ".bz2", "")
    /** Language */
    val lang: String = nameLang.split("_").last
    /** file name. ex: page_links*/
    val name: String = nameLang.replace("_"+ lang, "")

    //val outputDir = config.getProperty("org.dbpedia.spotlight.data.outputBaseDir","")
    Array(outputDir +  name + "." + lang + "." + input + ".obj.tsv" ,
        httpFile, 
        name + "_"+ lang + "_" + input)
  }
  
  def main(args : Array[String]) {
   
    // Creates an empty property list
    val config: Properties = new Properties()

    try {
      config.load(new FileInputStream(new java.io.File(args(0))))
    }
    catch {
      case e: IOException => {
        throw new ConfigurationException("Cannot find configuration file " + args(0), e)
      }
    }
  
    /** Loads the labels dataset if it is the first iteration**/
    //var first:Boolean = false 
    
    /** (0) output , (1) input file , (2) name**/
    /*val files: Array[String] = Array(
      "mappingbased_properties_en.nt.bz2",
      "page_links_en.nt.bz2",
      "infobox_properties_en.nt.bz2",
      "instance_types_pt.nt.bz2",
	    "mappingbased_properties_pt.nt.bz2",
      "page_links_pt.nt.bz2",
      "infobox_properties_en.nt.bz2"
      "page_links_unredirected_pt.nt.bz2",      
      "long_abstracts_pt.nt.bz2",
      "interlanguage_links_pt.nt.bz2",
      "instance_types_pt.nt.bz2",
      "mappingbased_properties_pt.nt.bz2",
      "page_links_en_uris_pt.nt.bz2",
      "interlanguage_links_same_as_pt.nt.bz2",
      "long_abstracts_en_uris_pt.nt.bz2",
      "infobox_properties_pt.nt.bz2",
      "infobox_properties_unredirected_pt.nt.bz2",
      "page_links_unredirected_en_uris_pt.nt.bz2",
      "short_abstracts_pt.nt.bz2",
      "infobox_properties_en_uris_pt.nt.bz2",
      "infobox_properties_unredirected_en_uris_pt.nt.bz2",
      "short_abstracts_en_uris_pt.nt.bz2",
      "interlanguage_links_same_as_chapters_pt.nt.bz2",
      "mappingbased_properties_unredirected_pt.nt.bz2",
      "wikipedia_links_pt.nt.bz2",
      "infobox_test_pt.nt.bz2",
      "revision_uris_pt.nt.bz2",
      "mappingbased_properties_en_uris_pt.nt.bz2",
      "mappingbased_properties_unredirected_en_uris_pt.nt.bz2",
      "revision_ids_pt.nt.bz2",
      "page_ids_pt.nt.bz2",
      "article_categories_pt.nt.bz2",
      "images_pt.nt.bz2",
      "images_en_uris_pt.nt.bz2",
      "external_links_pt.nt.bz2",
      "redirects_transitive_pt.nt.bz2",
      "redirects_pt.nt.bz2",
      "external_links_en_uris_pt.nt.bz2",
      "article_categories_en_uris_pt.nt.bz2",
      "iri_same_as_uri_pt.nt.bz2",
      "labels_en_uris_pt.nt.bz2",
      "instance_types_en_uris_pt.nt.bz2",
      "skos_categories_pt.nt.bz2",
      "interlanguage_links_see_also_pt.nt.bz2",
      "skos_categories_en_uris_pt.nt.bz2",
      "category_labels_pt.nt.bz2",
      "interlanguage_links_see_also_chapters_pt.nt.bz2",
      "disambiguations_pt.nt.bz2",
      "disambiguations_unredirected_pt.nt.bz2",
      "specific_mappingbased_properties_pt.nt.bz2",
      "specific_mappingbased_properties_en_uris_pt.nt.bz2",
      "category_labels_en_uris_pt.nt.bz2",
      "disambiguations_en_uris_pt.nt.bz2",
      "disambiguations_unredirected_en_uris_pt.nt.bz2",
      "homepages_pt.nt.bz2",
      "homepages_en_uris_pt.nt.bz2",
      "infobox_property_definitions_pt.nt.bz2",
      "infobox_property_definitions_en_uris_pt.nt.bz2",
      "geo_coordinates_pt.nt.bz2",
      "geo_coordinates_en_uris_pt.nt.bz2"
    )*/

    //var input: Array[String] = null

    /** Labels dataset parameters **/
    val tdbFile: String = config.getProperty("org.dbpedia.spotlight.dataset.inputFile","").trim
    val tdbLocation: String = config.getProperty("org.dbpedia.spotlight.dataset.location","").trim
    val tdbFormat: String = config.getProperty("org.dbpedia.spotlight.dataset.format","").trim
    
    val modelFile:String = config.getProperty("org.dbpedia.spotlight.extraction.inputFile","").trim
    val outputDir: String = modelFile.split("/").last.replace(".bz2", "").trim
    val outputName: String = outputDir + ".ctx"

    val modelFormat:String = config.getProperty("org.dbpedia.spotlight.extraction.inputFormat","").trim
    val extraction: String = config.getProperty("org.dbpedia.spotlight.extraction.type","").trim
    val outformat: String = config.getProperty("org.dbpedia.spotlight.extraction.outputFormat","").trim
    val outputFile: String = config.getProperty("org.dbpedia.spotlight.data.outputContext","").trim + "/" + outputName
    val namedModelLocation: String = config.getProperty("org.dbpedia.spotlight.data.tdbsOutputNL","").trim + "/" + outputDir
    val datasetsLocation: String = config.getProperty("org.dbpedia.spotlight.data.dbpediaInput","").trim

    extract2(true, //If it is necessary to load the default model. If is has already been loaded, pass false.
      tdbFile,
      tdbLocation,
      tdbFormat,
      modelFile,
      modelFormat,
      extraction,
      outformat,
      outputFile, 
      namedModelLocation,
      datasetsLocation) 
      
    /*for( i: String <- files){
    
      input = getFiles("http://downloads.dbpedia.org/3.8/en/" +  i, "nt", outputDir)
      
      //extract: reload,  modelFile, outputFile,  namedModel
      //input:   (0) outputFile , (1) input model file , (2) nameModel
      extractPropertiesJSON(first, input(1), input(0),  input(2) , tdbOutputDir, datasetInputDir)
   	  first = false 
    }*/

   
   }

}
