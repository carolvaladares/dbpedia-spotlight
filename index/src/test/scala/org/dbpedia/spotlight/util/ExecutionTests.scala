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
* Check our project website for informationxect:
* http://spotlight.dbpedia.org
*/

package org.dbpedia.spotlight.util

import java.io.{IOException, FileInputStream, InputStream}
import scala.io.Source
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.scalatest.Assertions
import java.util.Properties
import org.dbpedia.spotlight.exceptions.ConfigurationException

/**
 * Tests the functions of the project.
 *
 * @author reinaldo
 */
class ExecutionTests extends Assertions {

  // Creates an empty property list
  val config: Properties = new Properties()

  try {
    config.load(new FileInputStream(new java.io.File("conf/indexing.properties")))
  }
  catch {
    case e: IOException => {
      throw new ConfigurationException("Cannot find configuration file " + "conf/indexing.properties", e)
    }
  }

  /** TDB Input file*/
  val labelsNT: String = "http://downloads.dbpedia.org/3.8/pt/labels_pt.nt.bz2"						 
  val typesNT: String = "http://downloads.dbpedia.org/3.8/pt/instance_types_pt.nt.bz2"			
  val propertiesNT: String = "http://downloads.dbpedia.org/3.8/pt/mappingbased_properties_pt.nt.bz2"
  val linksNT: String =  "http://downloads.dbpedia.org/3.8/pt/page_links_en_uris_pt.nt.bz2"
    
  /** TDB Input file*/
  val labelsOWL: String = "http://dl.dropboxusercontent.com/u/10940054/pt/dbpedia_3.8.owl.bz2"
    
  /** TDB and Model Formats**/
  val formatNamed: String = "N-TRIPLE"
  val formatDefault: String = "RDF/XML"
    
  /** TDB Location **/
  val datasetNL: String = config.getProperty("org.dbpedia.spotlight.data.tdbOutputNL","")
  val datasetOWL: String = config.getProperty("org.dbpedia.spotlight.data.tdbOutputOWL","")
  
  /** Extraction params */
  val extractionObject: String = "object"
  val outputFormatTSV: String = "TSV"
  val extractionProperty: String = "property"
  val outputFormatJSON: String = "JSON"
      
  def TDBCreation() {
    /** Test the conversion of input file (*.bz2) into InputStrem **/
    val labelsInputNT: InputStream = BZipUntar.convert(labelsNT)
    val labelsInputOWL: InputStream = BZipUntar.convert(labelsOWL)
    
    assert(labelsInputNT != null)
    //assert(labelsInputOWL != null)
   
    /** Test NT and OWL databases creation **/
    DataConn.createTDBFilesystem(datasetNL, labelsInputNT, formatNamed)
    DataConn.createTDBFilesystem(datasetOWL, labelsInputOWL, formatDefault)
    
    /** Retrieves the database just created. **/
    DataConn.getTDBFilesystem(datasetNL)
    assert("AlbaniaHistory".equals(DataConn.executeQuery("http://dbpedia.org/resource/AlbaniaHistory")))
    
    DataConn.getTDBFilesystem(datasetOWL)
    assert("anatomical structure".equals(DataConn.executeQuery("http://dbpedia.org/ontology/AnatomicalStructure")))
  }
  
  @Before
  def createDatabases() {
    //TDBCreation
  }
  
  @Test
  def execution() {
    extract()
    //extractObjectsFromPageLinksAndNTDatabase
    //extractObjectsFromMapBasPropertiesAndNtDatabase
    //extractObjectsFromTypesAndNtDatabase
    //extractObjectsFromTypesAndOwlDatabase
    //extractPropertiesFromMapBasPropertiesAndOwlDatabase
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
  def getFiles(httpFile: String, input: String) : Array[String] = {
    
    /** file full name. ex: page_links_pt.nt.bz2 */
    val last: String = httpFile.split("/").last 
    /** file name with language. ex: page_links_pt*/
    val nameLang: String = last.replace("." + input + ".bz2", "")
    /** Language */
    val lang: String = nameLang.split("_").last
    /** file name. ex: page_links*/
    val name: String = nameLang.replace("_"+ lang, "")

    val outputDir = config.getProperty("org.dbpedia.spotlight.data.outputBaseDir","")
    Array(outputDir +  name + "." + lang + "." + input + ".obj.tsv" ,
        httpFile, 
        name + "_"+ lang + "_" + input)
  }
  
  /***
   * Extract all  datasets from dbpedia pt 3.8
   */
  def extract() {

    /** Loads the labels dataset if it is the first iteration**/
    var first:Boolean = false
    
    /** (0) output , (1) input file , (2) name**/
    val files: Array[String] = Array(
      "http://downloads.dbpedia.org/3.8/pt/instance_types_pt.nt.bz2"
      /*"https://dl.dropboxusercontent.com/u/10940054/mappingbased.bz2",
      "http://downloads.dbpedia.org/3.8/pt/page_links_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/page_links_unredirected_pt.nt.bz2",      
      "http://downloads.dbpedia.org/3.8/pt/long_abstracts_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/interlanguage_links_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/instance_types_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/mappingbased_properties_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/page_links_en_uris_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/interlanguage_links_same_as_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/long_abstracts_en_uris_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/infobox_properties_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/infobox_properties_unredirected_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/page_links_unredirected_en_uris_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/short_abstracts_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/infobox_properties_en_uris_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/infobox_properties_unredirected_en_uris_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/short_abstracts_en_uris_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/interlanguage_links_same_as_chapters_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/mappingbased_properties_unredirected_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/wikipedia_links_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/infobox_test_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/revision_uris_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/mappingbased_properties_en_uris_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/mappingbased_properties_unredirected_en_uris_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/revision_ids_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/page_ids_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/article_categories_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/images_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/images_en_uris_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/external_links_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/redirects_transitive_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/redirects_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/external_links_en_uris_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/article_categories_en_uris_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/iri_same_as_uri_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/labels_en_uris_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/instance_types_en_uris_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/skos_categories_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/interlanguage_links_see_also_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/skos_categories_en_uris_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/category_labels_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/interlanguage_links_see_also_chapters_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/disambiguations_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/disambiguations_unredirected_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/specific_mappingbased_properties_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/specific_mappingbased_properties_en_uris_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/category_labels_en_uris_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/disambiguations_en_uris_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/disambiguations_unredirected_en_uris_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/homepages_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/homepages_en_uris_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/infobox_property_definitions_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/infobox_property_definitions_en_uris_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/geo_coordinates_pt.nt.bz2",
      "http://downloads.dbpedia.org/3.8/pt/geo_coordinates_en_uris_pt.nt.bz2"
      */
    )

    var input: Array[String] = null
    for( i: String <- files){
      input = getFiles(i, "nt")
      //extract: reload,  modelFile, outputFile,  namedModel
      //input:   (0) outputFile , (1) input model file , (2) nameModel
      RDFContextExtractor.extractPropertiesJSON(first, input(1), input(0),  input(2))
   	  first = false 
    }
   
   asserts(input(0), "")
  }
  
  def extractObjectsFromPageLinksAndNTDatabase() {

    val outputFile: String = "files/outputs/__objectsFromPageLinksAndNtDatabase.tsv"
   
    RDFContextExtractor.extract(
      loadtdb = false,
      labelsNT, 
      datasetNL, 
      formatNamed, 
      typesNT, 
      formatNamed, 
      extractionProperty, 
      outputFormatJSON, 
      outputFile, 
      "linksnt")
  }
  
  def asserts(outputFile: String, assertPattern: String) {
    val source = Source.fromFile(outputFile)
    val lines = source.mkString
    println(lines)
    //assert(lines.equals(assertPattern))
    source.close()
  }
  
  def extractObjectsFromMapBasPropertiesAndNtDatabase() {
	  
    val outputFile: String = "files/outputs/objectsFromMapBasPropertiesAndNtDatabase.tsv"
   
    RDFContextExtractor.extract(
      loadtdb = true,
      labelsNT, 
      datasetNL, 
      formatNamed, 
      propertiesNT, 
      formatNamed, 
      extractionObject, 
      outputFormatTSV, 
      outputFile,
     "propertiesNT")
      
    //asserts(outputFile, "")
    
    /*"""Aristotle	Metaphysics Theatre Biology  Galileo_Galilei List of writers influenced by Aristotle Alexander_the_Great Albertus_Magnus Christian_philosophy Socrates , Aristot√©lƒìs Science Music Ethics Parmenides Politics Democritus Zoology -0384 Duns_Scotus Rhetoric Peripatetic_school Western_philosophy Thomas_Aquinas Physics Government Avicenna Reason Logic Nicolaus_Copernicus Western_philosophy Aristotelianism Syllogism Heraclitus Ptolemy -0322 Poetry Jewish_philosophy Maimonides Plato Ancient_philosophy Islamic_philosophy Averroes
		Animal_Farm	Animal Farm: A Fairy Story George_Orwell 53163540 112 Nineteen_Eighty-Four ISBN 0-452-28424-4 (present) ISBN 978-0-452-28424-1 Harvill_Secker 823/.912 20 _Socialism_and_the_English_Genius PR6029.R8 A63 2003b Animal Farm
		Autism	Autism D001321 3202 med 001526 209850 299.00 1142
		Alabama	_Alabama English_American Alabamian State of Alabama United_States
		"""
    */

  }

  def extractObjectsFromTypesAndNtDatabase() {

    val outputFile: String = "files/outputs/test/proFromMappingAndNtDatabase.tsv"
   
    RDFContextExtractor.extract2(
      loadtdb = false,
      labelsNT, 
      datasetNL, 
      formatNamed, 
      "/Users/carol/Documents/Intrinsic/Dataset/mappingbased_properties_pt.nt", 
      formatNamed, 
      extractionProperty, 
      outputFormatTSV, 
      outputFile,
      "/Users/carol/Documents/Intrinsic/Repositories/spotlight-util/files/outputs/test/objMappNt" ,
	  "/Users/carol/Documents/Intrinsic/Dataset")
      
    // asserts(outputFile, "")
    
    /*"""Allan_Dwan	Agent Person Thing Person Person
		Allan_Dwan__2	Thing PersonFunction
		Academy_Award_for_Best_Art_Direction	Thing Award
		Actrius	Thing CreativeWork Work Movie Film
		Animal_Farm	CreativeWork Work WrittenWork Thing Book Book Book
		Abraham_Lincoln	Agent Person Thing Person Person OfficeHolder
		Allan_Dwan__1	Thing PersonFunction
		Alain_Connes	Agent Person Thing Person Person Scientist
		Academy_Award	Thing Award
		Alabama	Thing Place Place PopulatedPlace AdministrativeArea AdministrativeRegion
		Autism	Thing Disease
		Aristotle	Agent Person Thing Person Person Philosopher
		America_the_Beautiful	Thing CreativeWork Work Musical Work MusicRecording Song
		Ayn_Rand	Artist Writer
		Allan_Dwan__3	Thing PersonFunction
		"""*/
  }

  def extractObjectsFromTypesAndOwlDatabase() {

    val outputFile: String = "files/outputs/objectsFromTypesAndOwlDatabase.tsv"
   
    RDFContextExtractor.extract(
      loadtdb = false,
      labelsOWL, 
      datasetOWL, 
      formatDefault, 
      typesNT, 
      formatNamed, 
      extractionObject, 
      outputFormatTSV, 
      outputFile,
      "typesNT_owl")
      
     asserts(outputFile, "")
    
    /* assertEquals("""Allan_Dwan	agent Person Thing Person Person
		Allan_Dwan__2	Thing person function
		Academy_Award_for_Best_Art_Direction	Thing Auszeichnung
		Actrius	Thing CreativeWork Werk Movie Film
		Animal_Farm	CreativeWork Werk written work Thing Book Book Buch
		Abraham_Lincoln	agent Person Thing Person Person Amtsinhaber
		Allan_Dwan__1	Thing person function
		Alain_Connes	agent Person Thing Person Person Wissenschaftler
		Academy_Award	Thing Auszeichnung
		Alabama	Thing Place Ort populated place AdministrativeArea administrative region
		Autism	Thing Krankheit
		Aristotle	agent Person Thing Person Person Philosoph
		America_the_Beautiful	Thing CreativeWork Werk musical work MusicRecording song
		Ayn_Rand	K√ºnstler ÏûëÍ∞Ä
		Allan_Dwan__3	Thing person function
		""", lines)*/
  
  }

  def extractPropertiesFromMapBasPropertiesAndOwlDatabase() {

    val outputFile: String = "files/outputs/2.tsv"
       //"property", "JSON", "files/inputs/3.8_sl_en_sl_mappingbased_properties_en.nt",
     // "files/outputs/_propertiesFromMapBasPropertiesAndOwlDatabase.tsv"
   /* RDFContextExtractor.extract(
      false, 
      labelsNT, 
      datasetNL, 
      formatDefault, 
      "files/inputs/3.8_sl_en_sl_mappingbased_properties_en.nt", 
      formatNamed, 
      "property", 
      outputFormatJSON, 
      outputFile,
      "propertiesNT99")
      
      */
      
      
     RDFContextExtractor.extract2(
      loadtdb = true,
      labelsNT, 
      datasetNL, 
      formatNamed, 
      "files/inputs/3.8_sl_en_sl_mappingbased_properties_en.nt", 
      formatNamed, 
      "property", 
      outputFormatJSON, 
      outputFile,
      "/Users/carol/Documents/Intrinsic/Repositories/spotlight-util/files/outputs/test/test" ,
	  "/Users/carol/Documents/Intrinsic/Dataset")
	  
     //asserts(outputFile, "")
    
    /* assertEquals("""Aristotle	{(death,1),(œÄŒµœÅŒπŒøœáŒÆ,1),(birth,1),(nam,1),(interest,11),(by,5),(year,2),(notableide,4),(main,11),(era,1),(influenced,21),(philosophicalschool,2)}
		Animal_Farm	{(nam,2),(decimal,1),(author,1),(dewey,1),(previous,1),(classification,1),(work,2),(of,1),(subsequent,1),(numb,1),(pag,1),(isbn,1),(oclc,1),(herausgeb,1),(lcc,1)}
		Autism	{(nam,1),(medlineplus,1),(subject,1),(icd9,1),(mesh,1),(omim,1),(id,2),(emedicin,2),(diseasesdb,1),(topic,1)}
		Alabama	{(nam,1),(hauptstadt,1),(demonym,1),(œáœéœÅŒ±,1),(sprach,1)}
		""", lines)*/
  }

  @After
  def removeOutputFilesOfTest() {

    //assert(new File("files/outputs/propertiesFromMapBasPropertiesAndOwlDatabase.tsv").delete)
    //assert(new File("files/outputs/objectsFromTypesAndNtDatabase.tsv").delete)
    //assert(new File("files/outputs/objectsFromTypesAndOwlDatabase.tsv").delete)
    //assert(new File("files/outputs/objectsFromMapBasPropertiesAndNtDatabase.tsv").delete)

  }
}
