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
*
*/

package org.dbpedia.spotlight.util

import com.hp.hpl.jena.util.FileManager
import com.hp.hpl.jena.vocabulary.RDF
import com.hp.hpl.jena.rdf.model._
import scala.util.matching.Regex
import scala.io.Source
import scala.util.control.Breaks._
import java.io._
import org.apache.commons.logging.LogFactory
import org.apache.commons.io.FileUtils
import org.apache.http.util.EntityUtils
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
import com.google.api.client.http.GenericUrl
import scala.util.parsing.json.JSON
import scala.Predef._
import scala.Some
import scala.collection.mutable.ListBuffer
import scala.util.control.Breaks
import TDBHelper._
import ArrayUtil._

class ComplementTypes() {
  //                                                 "en"
  val allLanguagesArray = List("af","al","am","an","ar","as","az",
                               "bg","bn","bp","br","bu","ca","cs","de","dbpedia","el","es","fr","hu","it","ko","pl","pt","ru","sl","tr")

  // Utility function to append the final string to the initial instance types triples file
  def using[A <: {def close(): Unit}, B](param: A)(f: A => B): B = {
    try { f(param) } finally { param.close() }
  }

  def appendToFile(fileName:String, textData:String) = {
    using (new FileWriter(fileName, true)){
      fileWriter => using (new PrintWriter(fileWriter)) {
        printWriter => printWriter.println(textData)
      }
    }
  }

  def getIndex(aDBpediaResource: String): Int = {
    val targetLanguage = aDBpediaResource.split("/")(2).split("""\.""")(0)
    //println(aDBpediaResource)
    //println(targetLanguage)
    var index = -1
    for (i <- 0 until ComplementTypes.compLangsArray.length) {
      //println("Target lang = " + targetLanguage + " Lang = " + ComplementTypes.compLangsArray(i))
      if (targetLanguage == ComplementTypes.compLangsArray(i)) {
        index = i
        return index
      }
    }
    index
  }

  def reformatString(aResourceGroup: String): String = {
    val aLength = aResourceGroup.split(" ").length
    //println(aResourceGroup)

    var initialIndex = -1
    var finalIndex = -1
    for (resource <- aResourceGroup.split(" ")) {
      val currentIndex = getIndex(resource)
      if (initialIndex == -1 && currentIndex != -1) {
        initialIndex = currentIndex
      }
      if (currentIndex > finalIndex) {
        finalIndex = currentIndex
      }
    }

    //initialIndex = getIndex(aResourceGroup.split(" ")(0).reverse.dropRight(1).reverse)
    //finalIndex = getIndex(aResourceGroup.split(" ")(aLength-1))
    var finalString = ""// ("\N " * (finalIndex-initalIndex+1)).dropRight(1)

    //println(initialIndex)
    //println(finalIndex)

    if (initialIndex == -1 && finalIndex == -1) {
      ""
    } else {
      val groupArray = aResourceGroup.split(" ")
      groupArray(0) = groupArray(0).reverse.dropRight(1).reverse
      var auxString = ""
      if (ComplementTypes.compLangsArray.length-1 > finalIndex - initialIndex) {
        initialIndex = 0
        finalIndex = ComplementTypes.compLangsArray.length-1
      }
      for (i <- initialIndex to finalIndex) {
        for (j <- 0 until groupArray.length) {
          if (ComplementTypes.compLangsArray(i) == groupArray(j).split("/")(2).split("""\.""")(0) && auxString == "") {
            auxString += groupArray(j) + " "
          }
        }
        //println(auxString)
        //new java.util.Scanner(System.in).nextLine()
        if (auxString == "") {
          finalString += """\N """
        } else {
          finalString += auxString
        }
        auxString = ""
      }
    }

    //println(finalString.dropRight(1))
    //val sortedList = finalString.reverse.dropRight(1).reverse.split(" ").toList.sortWith(_ < _)
    //val sortedList = finalString.split(" ").toList.sortWith(_ < _)
    finalString = "<" + finalString.dropRight(1)
    //for (aResource <- sortedList) {
    //  finalString += aResource + " "
    //}
    //println(finalString)
    //new java.util.Scanner(System.in).nextLine()
    //System.exit(1)
    finalString
  }

  def fixEnEntry(anEntryGroup: Array[String]) {
    // Replacing the "en" word with "dbpedia" so we can find it
    for(i <- 0 until anEntryGroup.length) {
      if (ComplementTypes.compLangsArray(i) == "en") {
        ComplementTypes.compLangsArray(i) = "dbpedia"
      }
    }
  }

  def filterFile(aPath: String): String = {
    val newPath = aPath + """.new"""
    val lines = Source.fromFile(aPath).getLines()

    fixEnEntry(ComplementTypes.compLangsArray)

    for (line <- lines.drop(1)) {
      //println(line)
      for (language <- ComplementTypes.compLangsArray) {
        val resource = line.split(" ")(2)
        //println(resource.split("/")(2))
        try {
          if (resource.split("/")(2).split("""\.""")(0) == language) {
            appendToFile(newPath, line)
          }
        } catch {
          case e: Exception =>
        }
      }
    }
    newPath
  }

  def rebuildInterlanguageLinksFile(aPath: String) {
    //val aNewPath = filterFile(aPath)
    //System.exit(1)
    val aNewPath = aPath + """.new"""
    val lines = (Source fromFile aNewPath).getLines()
    val firstLineColumns = lines.drop(1).take(1).toList

    // Only one column, this is the initial line string
    val firstLine = firstLineColumns(0)

    // Three columns:
    // <resource_main_lang> <sameAs> <resource_another_lang>
    var resourceMainLanguage = firstLine.split(" ")(0)
    var sameAs = firstLine.split(" ")(1)
    var resourcesGroup = firstLine.split(" ")(2).dropRight(3)

    // Remove!
    fixEnEntry(ComplementTypes.compLangsArray)

    // Read line except the first one which is the timestamp
    for (line <- (Source fromFile aPath).getLines().drop(1)) {
      val columns = line.split(" ")
      if (columns(0) == resourceMainLanguage) {
        resourcesGroup += " " + columns(2).reverse.dropRight(1).reverse.dropRight(3)
      } else {
        resourcesGroup = reformatString(resourcesGroup)
        if (resourcesGroup != "") {
          resourcesGroup += "> ."
          appendToFile("E:/Spotlight/interlanguage_links_same_as_pt_columns", resourceMainLanguage+'\t'+sameAs+'\t'+resourcesGroup)
        }
        val lineArray = line.toString.dropRight(3).split(" ")
        resourceMainLanguage = lineArray(0)
        sameAs = lineArray(1)
        resourcesGroup = lineArray(2)
        //resourceMainLanguage = columns(0)
      }
    }
    System.exit(1)
  }

  /*def rebuildInterlanguageLinksFile(aPath: String) {
    val lines = (Source fromFile aPath).getLines()
    val firstLineColumns = lines.drop(1).take(1).toList
    var stringToAppend = firstLineColumns(0).dropRight(3)
    var firstColumn = stringToAppend.split(" ")(0)
    for (line <- (Source fromFile aPath).getLines().drop(1)) {
      val columns = line.split(" ")
      //println(unescapeJava(columns(0)))
      //println(unescapeJava(firstColumn))
      //if (unescapeJava(columns(0)) == unescapeJava(firstColumn)) {
      if (columns(0) == firstColumn) {
        stringToAppend += " " + columns(2).reverse.dropRight(1).reverse.dropRight(3)
      } else {
        stringToAppend += "> ."
        appendToFile("E:/Spotlight/interlanguage_links_pt_new", stringToAppend)
        stringToAppend = line.toString.replaceAll(" ", "\t").dropRight(3)
        firstColumn = columns(0)
      }
    }
  }   */

  def preprocessDbFbFiles(aMainLanguage:String, aFbLinksFile: String, aMainEnLinksFile: String, anOutputPath: String) {
    val loop = new Breaks
    var i = 0
    var j = 0
    var auxIndex = 0
    for (lineA <- (Source fromFile aMainEnLinksFile).getLines()) {
      val lineAList = lineA.split(" ").toList
      //println("DERP" + lineAList.mkString)
      loop.breakable {
        for (lineB <- (Source fromFile aFbLinksFile).getLines()) {
          if (j >= auxIndex) {
            val lineBList: List[String] = lineB.split(" ").toList
            //println("ESSE AI... " + lineAList(2)(29) + " MAIOR DO QUE " + lineBList.head(29))
            lineBList match {
              case a if lineBList.head(29) > lineAList(2)(29) => {
                //println("ESSE AI... " + lineAList(2)(29) + " MAIOR DO QUE " + lineBList.head(29))
                //System.exit(1)
                j = 0
                loop.break()
              }
              //TODO: Add Scala 2.10 StringContext class for regex comparison, some strings are not getting caught here
              case b if lineAList(2) == lineBList.head => {
                auxIndex = j
                val finalString = lineAList(2) + " <http://www.w3.org/2002/07/owl#sameAs> " + lineBList(2)
                appendToFile(anOutputPath + aMainLanguage + "_mid.nt", finalString)
                //println(finalString)
                println("Hum j = " + j.toString + " e aux = " + auxIndex.toString)
                j = 0
                loop.break()
              }
              case _ =>
            }
          }
          j += 1
        }
      }
      j = 0
      i += 1
      if (i % 10000 == 0) {
        println("Entries " + i + "...")
      }
    }
  }

  // We need an update method in case we update models during execution
  def addStatementToModel(aSubject:String, aPredicate:String, anObject:String, aModel:Model) {
    val aSubjectNode = ResourceFactory.createResource(aSubject)
    val anObjectNode = ResourceFactory.createResource(anObject)
    val aStatement = ResourceFactory.createStatement(aSubjectNode, RDF.`type`, anObjectNode)
    aModel.add(aStatement); // add the statement (triple) to the model
  }

  // A method that uses types from Freebase to complement DBpedia resources types remotely, requires a google API key
  def compTypesWithFreebaseRemote(){
    println("Starting types complement using an endpoint of the freebase...")
    // Get the information we need from the companion object
    val mainLanguage = ComplementTypes.mainLanguage(0)
    val freebaseBaseDir = ComplementTypes.freebaseBaseDir
    val outputBaseDir = ComplementTypes.outputBaseDir
    val dbpediaBaseDir = ComplementTypes.dbpediaBaseDir

    // We need to make an implicit extraction of types in scala to prevent runtime errors
    // when trying to cast objects from one class loader to another
    implicit def any2string(a: Any)  = a.toString
    implicit def any2boolean(a: Any) = a.asInstanceOf[Boolean]
    implicit def any2double(a: Any)  = a.asInstanceOf[Double]
    case class Language(aId: String, aType: String)

    // Create a client to execute all of our requests
    val httpClient = new DefaultHttpClient()

    // In the case of complementing types using Freebase we only delete the TDB store files to prevent NULL pointer exceptions.
    // This line can be commented out if the model was generated correctly in a previous execution of this method. No statements
    // are added to this model during execution
    FileUtils.cleanDirectory(new java.io.File(freebaseBaseDir + "/TDB"))

    // Create a model to represent the links between DBpedia and Freebase
    val fbTdbStore = createModel(freebaseBaseDir + "/TDB")
    FileManager.get().readModel( fbTdbStore, freebaseBaseDir + "freebase_links.nt", "N-TRIPLES" )
    println(freebaseBaseDir + "freebase_links.nt")

    val ptEnFileIterator = createNTFileIterator(outputBaseDir + mainLanguage + "/" + mainLanguage + "_en_links.nt")

    // Join the files to speed up the process. Usage arguments: Main language, the links file from DBpedia to Freebase,
    // the links file from the main language to the english language and the output path
    preprocessDbFbFiles(mainLanguage, freebaseBaseDir + "freebase_links_sorted.nt", outputBaseDir + mainLanguage + "_en_links_sorted.nt", outputBaseDir + mainLanguage)

    while (ptEnFileIterator.hasNext) {
      val stmt = ptEnFileIterator.nextStatement()
      val enObject = stmt.getObject.toString

      val dbFbOccsQuery = buildQueryOWLSameAs(enObject)
      val dbFbOccsResults = executeQuery(dbFbOccsQuery, fbTdbStore)

      while (dbFbOccsResults.hasNext) {
        val soln = dbFbOccsResults.nextSolution()
        val fbObject = soln.get("o").toString.replace("http://rdf.freebase.com/ns/","").replace('.','/')

        // Print the freebase object, for debugging purposes
        //println(fbObject)

        // The endpoint from where we get the types related to a resource
        val url = new GenericUrl("https://www.googleapis.com/freebase/v1/mqlread")
        url.put("query", """[{"id":"/""" + fbObject + """","type":[]}]""")

        // Print the final url, for debugging purposes
        //println(url)

        // Actually executing the request for types. This uses Apache http because
        // the Google API does not fully support Java and Scala
        val httpResponse = httpClient.execute(new HttpGet(url.toString))

        // The JSON string to be parsed
        val response = JSON.parseFull(EntityUtils.toString(httpResponse.getEntity))

        // Some simple pattern matching to get the initial set
        var canProcess = false
        response match {
          case Some(x) => {
            val m = x.asInstanceOf[Map[String, List[Map[String, Any]]]]
            if (m contains "result") {
              m("result") map {l => Language(l("id"), l("type"))}
              canProcess = true
            }
          }
          case None => {
            canProcess = false
          }
        }

        if (canProcess) {
          // String buffer to hold the types of each resource
          var typesBuffer = new ListBuffer[String]()

          // At this point we got a class of type Some so in order to make it a Map we use the get method
          val initialMap = response.get

          // The first loop gets all Tuples inside the initial Map. We only have one internal Tuple at this point since the freebase
          // result is is mapped by "result -> List(Map(type -> List(type1, type2, ..., typeN)))"
          for (initialTuple <- initialMap.asInstanceOf[Map[String,List[Map[String,List[String]]]]]) {
            // Change the Tuple to a List so we can reach the internal Map
            val internalList: List[Map[String,List[String]]] = initialTuple._2.asInstanceOf[List[Map[String,List[String]]]].toList
            // The internal mapping gives us the relation of "type -> List(type1, type2, ..., typeN)". At this point
            // the class type changes to $colon$colon
            val internalMapColon = internalList(0)
            for (internalMap <- internalMapColon.asInstanceOf[Map[String,List[String]]]) {
              // There are two possible cases here. The first one is "id -> string" and the second one
              // is "type -> List(type1, type2, ..., typeN)" so we match accordingly
              internalMap match {
                case input: (String, List[String]) => {
                  if (input._2.isInstanceOf[List[String]]) {
                    // If we reached this part it means that we only need to get the types for this resource
                    val typeList = input._2
                    for (aType <- typeList) {
                      // Save the types to the buffer
                      typesBuffer += aType
                    }
                  }
                }
                case _ =>
              }
            }
          }

          // Build the final string to be added
          for (aType <- typesBuffer) {
            // <subject> <predicate> <object>
            val finalString = "<" + stmt.getSubject.toString + "> <" + RDF.`type` + "> <http://rdf.freebase.com/ns/" + aType + "> ."

            // Displays on the screen the final string to be added to the subject
            // instance types triples file. For debugging purposes
            //System.out.println(finalString)

            // We can now concatenate the final string to the current instance types file
            appendToFile(dbpediaBaseDir + mainLanguage + '/' + "instance_types_" + mainLanguage + ".nt", finalString)
          }

          typesBuffer --= typesBuffer
        }
      }
    }

    // Close the datasets now that we are done
    fbTdbStore.close()
    println("Done!")
  }

  // A method that uses types from Freebase to complement DBpedia resources types locally, requires pre-processing.
  // More information in the freebase_types.sh script. Recommended memory of 24GB
  def compTypesWithFreebaseLocal(){
    println("Starting types complement using a local version of the freebase...")
    // Get the information we need from the companion object
    val mainLanguage = ComplementTypes.mainLanguage(0)
    val freebaseBaseDir = ComplementTypes.freebaseBaseDir
    val outputBaseDir = ComplementTypes.outputBaseDir
    val dbpediaBaseDir = ComplementTypes.dbpediaBaseDir

    // In the case of complementing types using Freebase we only delete the TDB store files to prevent NULL pointer exceptions.
    // These lines can be commented out if the model was generated correctly in a previous execution of this method. No statements
    // are added to these models during execution
    FileUtils.cleanDirectory(new java.io.File(freebaseBaseDir + "/TDB"))
    FileUtils.cleanDirectory(new java.io.File(freebaseBaseDir + "/MID_TDB"))

    // Create a model to represent the links between DBpedia and Freebase
    val fbTdbStore = createModel(freebaseBaseDir + "/TDB")
    FileManager.get().readModel( fbTdbStore, freebaseBaseDir + "freebase_links.nt", "N-TRIPLES" )

    // Create a model to represent a part of the Freebase that we are interested
    val fbMidTdbStore = createModel(freebaseBaseDir + "/MID_TDB")
    FileManager.get().readModel( fbMidTdbStore, freebaseBaseDir + "freebase_mid_types.nt", "N-TRIPLES" )

    val ptEnFileIterator = createNTFileIterator(outputBaseDir + mainLanguage + "/" + mainLanguage + "_en_links.nt")

    while (ptEnFileIterator.hasNext) {
      val stmt = ptEnFileIterator.nextStatement()
      val enObject = stmt.getObject.toString

      val dbFbOccsQuery = buildQueryOWLSameAs(enObject)
      val dbFbOccsResults = executeQuery(dbFbOccsQuery, fbTdbStore)

      while (dbFbOccsResults.hasNext) {
        val soln = dbFbOccsResults.nextSolution()
        val fbObject = soln.get("o").toString.replace("http://rdf.freebase.com/ns/","ns:")

        for (line <- (Source fromFile (freebaseBaseDir + "freebase_ids")).getLines()) {
          // If we have a MID that is guaranteed to link DBpedia with Freebase
          if (line.toString.matches(fbObject)) {
            val fbTypesQuery = buildQueryRDFType(line.toString)
            val fbTypesResults = executeQuery(fbTypesQuery, fbMidTdbStore)

            while (fbTypesResults.hasNext) {
              val soln = fbTypesResults.nextSolution()
              val tmpString = soln.get("o").toString.replace(':','/')
              val aFbType = "http://rdf.freebase.com/" + tmpString.replace('.','/')

              // <subject> <predicate> <object>
              val finalString = "<" + stmt.getSubject.toString + "> <" + RDF.`type` + "> <" + aFbType + "> ."

              // Displays on the screen the final string to be added to the subject
              // instance types triples file. For debugging purposes
              System.out.println(finalString)

              // We can now concatenate the final string to the current instance types file
              appendToFile(dbpediaBaseDir + mainLanguage + '/' + "instance_types_" + mainLanguage + ".nt", finalString)
            }
            break()
          }
        }
      }
    }

    // Close the datasets now that we are done
    fbTdbStore.close()
    fbMidTdbStore.close()
    println("Done!")
  }

  // A method to complement types of one language using others. All the information is defined in the indexing.properties file
  // and is loaded in the companion object
  def compTypesWithOtherLanguages() {
    println("Starting types complement using other languages...")
    // Get the information we need from the companion object
    val mainLanguage = ComplementTypes.mainLanguage(0)
    val allLangsArray = ComplementTypes.allLangsArray
    val instTypesNamesArray = ComplementTypes.instTypesNamesArray
    val fileIteratorArray = ComplementTypes.fileIteratorArray
    val tdbStoreArray = ComplementTypes.tdbStoreArray
    val dbpediaBaseDir = ComplementTypes.dbpediaBaseDir
    val tdbStoreBaseDir = ComplementTypes.tdbStoreBaseDir
    val outputBaseDir = ComplementTypes.outputBaseDir

    val aLinksFile = ComplementTypes.linksFile

    // Every time this method is called we delete the TDB store files for the main language and create new ones. This happens
    // because as we generate statements and add them to the main language instance types file, we also modify the model
    // for this language. If we do not delete the files Jena would create a model using the old files. This also guarantees
    // that no NULL pointer exceptions will be thrown at this point
    var i = 0
    for( langName <- allLangsArray ){
      instTypesNamesArray(i) = "instance_types_" + langName + ".nt"
      println(instTypesNamesArray(i))
      println(tdbStoreBaseDir + langName + "/TDB")
      println(dbpediaBaseDir + langName + '/' + instTypesNamesArray(i))
      FileUtils.cleanDirectory(new java.io.File(tdbStoreBaseDir + langName + "/TDB"))
      tdbStoreArray(i) = createModel(tdbStoreBaseDir + langName + "/TDB")
      FileManager.get().readModel( tdbStoreArray(i), dbpediaBaseDir + langName + '/' + instTypesNamesArray(i), "N-TRIPLES" )
      i += 1
    }

    // Now for each bijective inter languages links file we proceed with the core routine of the language types complement.
    // First it executes the query over the main language instance types file to see if there are types for the current resource.
    // If not, executes another query using the sameAs relation in the complement language instance types file. In the end the
    // routine adds the results to the current instance types file of the main language, complementing it
    val aLinksFileIterator = createNTFileIterator(aLinksFile)
    while (aLinksFileIterator.hasNext) {
      val stmt = aLinksFileIterator.nextStatement()
      val subject = stmt.getSubject.toString
      val obj = stmt.getObject.toString

      // The idea is to query the instance types file related to the object in order to complement the instance types file
      // related to the subject. For instance, if in portuguese a subject has no types associated to it, we can
      // use other languages to find dbpedia types for it
      val newSubject = obj

      // A query to find if the subject from the main language has any types in the instance types triples file
      val occsQuery = buildQueryRDFType(subject)
      val occsResults = executeQuery(occsQuery, tdbStoreArray(0))

      //TODO, if the subject has at least one type, search for new types in the object instance types file and add them accordingly, a join operation
      /*if (occsResults.hasNext()) {
        while (occsResults.hasNext()) {

        }
      } else {*/
      if (!occsResults.hasNext) {
        val langsColumnsArray = obj.split(" ")
        langsColumnsArray(0) = langsColumnsArray(0).reverse.dropRight(1).reverse
        for(langsColumn <- langsColumnsArray) {
          // A query to find the types of a subject in the instance types triples file
          val newTypesQuery = buildQueryRDFType(langsColumn)
          val newTypesResult = executeQuery(newTypesQuery, tdbStoreArray(i))

          // Make a pattern so we can replace the owl occurrences
          val pattern = new Regex("(O|o)wl")

          if (newTypesResult.hasNext) {
            // Iterating the ResultSet to get all its elements
            while (newTypesResult.hasNext) {
              // <subject> <predicate> <object>
              var finalString = "<" + subject + "> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> "
              val soln = newTypesResult.nextSolution()
              var newObject = ""

              if ((pattern findFirstMatchIn soln.toString) != None) {
                newObject = "http://www.w3.org/2002/07/owl#Thing"
              } else {
                newObject = soln.get("o").toString
              }
              finalString = finalString + "<" + newObject + "> ."

              // Displays on the screen the final string to be added to the subject
              // instance types triples file. For debugging purposes
              //System.out.println(finalString)

              // We can now concatenate the final string to the current instance types file and update the
              // current model
              appendToFile(dbpediaBaseDir + mainLanguage + '/' + instTypesNamesArray(0), finalString)
              addStatementToModel(subject, RDF.`type`.toString, newObject, tdbStoreArray(0))
            }
            break()
          }
        }
      }
    }


    /*// Now for each bijective inter languages links file we proceed with the core routine of the language types complement.
    // First it executes the query over the main language instance types file to see if there are types for the current resource.
    // If not, executes another query using the sameAs relation in the complement language instance types file. In the end the
    // routine adds the results to the current instance types file of the main language, complementing it
    i = 1
    for( aLinksFile <- fileIteratorArray ){
      while (aLinksFile.hasNext) {
        val stmt = aLinksFile.nextStatement()
        val subject = stmt.getSubject.toString
        val obj = stmt.getObject.toString

        // The idea is to query the instance types file related to the object in order to complement the instance types file
        // related to the subject. For instance, if in portuguese a subject has no types associated to it, we can
        // use other languages to find dbpedia types for it
        val newSubject = obj

        // A query to find if the subject from the main language has any types in the instance types triples file
        val occsQuery = buildQueryRDFType(subject)
        val occsResults = executeQuery(occsQuery, tdbStoreArray(0))

        // A query to find the types of a subject in the instance types triples file
        val newTypesQuery = buildQueryRDFType(newSubject)
        val newTypesResult = executeQuery(newTypesQuery, tdbStoreArray(i))

        //TODO, if the subject has at least one type, search for new types in the object instance types file and add them accordingly, a join operation
        /*if (occsResults.hasNext()) {
          while (occsResults.hasNext()) {

          }
        } else {*/
        if (!occsResults.hasNext) {

          // Make a pattern so we can replace the owl occurrences
          val pattern = new Regex("(O|o)wl")

          // Iterating the ResultSet to get all its elements
          while (newTypesResult.hasNext) {
            // <subject> <predicate> <object>
            var finalString = "<" + subject + "> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> "
            val soln = newTypesResult.nextSolution()
            var newObject = ""

            if ((pattern findFirstMatchIn soln.toString) != None) {
              newObject = "http://www.w3.org/2002/07/owl#Thing"
            } else {
              newObject = soln.get("o").toString
            }
            finalString = finalString + "<" + newObject + "> ."

            // Displays on the screen the final string to be added to the subject
            // instance types triples file. For debugging purposes
            //System.out.println(finalString)

            // We can now concatenate the final string to the current instance types file and update the
            // current model
            appendToFile(dbpediaBaseDir + mainLanguage + '/' + instTypesNamesArray(0), finalString)
            addStatementToModel(subject, RDF.`type`.toString, newObject, tdbStoreArray(0))
          }
        }
      }
      i += 1
    }*/

    // Close the datasets now that we are done
    i = 0
    for( langName <- allLangsArray ){
      tdbStoreArray(i).close()
      i += 1
    }
    println("Done!")
  }
}

object ComplementTypes {
  private val LOG = LogFactory.getLog(this.getClass)

  val aTypeManager = new ComplementTypes()
  val config = new IndexingConfiguration()

  // Read in from the indexing.properties files all the data we need. Creates arrays accordingly
  val mainLanguage = Array(config.get("org.dbpedia.spotlight.language_i18n_code", ""))
  val compLangsArray = config.get("org.dbpedia.spotlight.complement_languages", "").split(",").toArray
  //val compLangsArray = config.get("org.dbpedia.spotlight.all_languages", "").split(",").toArray
  val allLangsArray = mainLanguage ++ compLangsArray

  // Checks if the number of languages is not valid
  testArrayLength(1, allLangsArray.length, LOG)

  // Get the base directories used in this process. The user can set the paths to them in the indexing.properties file.
  // If the download.sh script was executed with the complement types option, all the needed directories were already created
  // and are defined inside the script.
  val tdbStoreBaseDir = config.get("org.dbpedia.spotlight.data.tdbStoreBaseDir","")
  val dbpediaBaseDir = config.get("org.dbpedia.spotlight.data.dbpediaBaseDir","")
  val outputBaseDir = config.get("org.dbpedia.spotlight.data.outputBaseDir","")
  val freebaseBaseDir = config.get("org.dbpedia.spotlight.data.freebaseBaseDir","")

  // Creates an array to hold all the names of the instance types files we are going to need
  val instTypesNamesArray = new Array[String](allLangsArray.length)
  // Creates an array to hold all models so we can query the instance types files from all the required languages
  val tdbStoreArray = new Array[Model](allLangsArray.length)
  // Creates an iterator array so we can check every resource of the main language related to each complement language
  val fileIteratorArray = new Array[StmtIterator](allLangsArray.length-1)

  val linksFile = "E:/Spotlight/interlanguage_links_same_as_pt_columns"

  // Checks if the number of files to iterate through is not valid
  testArrayLength(1, fileIteratorArray.length, LOG)

  def main(args : Array[String]) {
    //aTypeManager.rebuildInterlanguageLinksFile("E:/Spotlight/interlanguage_links_same_as_pt.nt")

    // Core methods for the types complement task
    aTypeManager.compTypesWithOtherLanguages()
    //aTypeManager.compTypesWithFreebaseLocal()
    //aTypeManager.compTypesWithFreebaseRemote()
    //aTypeManager.preprocessDbFbFiles(mainLanguage(0), freebaseBaseDir + "freebase_links_sorted.nt", outputBaseDir + mainLanguage(0) + '/' + mainLanguage(0) + "_en_links_sorted.nt", outputBaseDir + mainLanguage(0) + '/')
  }
}