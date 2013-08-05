package org.dbpedia.spotlight.lucene.index

/**
 * Created with IntelliJ IDEA.
 * User: Renan
 * Date: 02/08/13
 * Time: 16:57
 * To change this template use File | Settings | File Templates.
 */

import org.dbpedia.spotlight.util.{IndexingConfiguration, TypesLoader}
import com.hp.hpl.jena.rdf.model.ModelFactory
import com.hp.hpl.jena.rdf.model.Model
import com.hp.hpl.jena.rdf.model.Resource
import com.hp.hpl.jena.rdf.model.StmtIterator
import com.hp.hpl.jena.util.FileManager
import com.hp.hpl.jena.query.ARQ
import com.hp.hpl.jena.query.Query
import com.hp.hpl.jena.query.QueryExecution
import com.hp.hpl.jena.query.QueryExecutionFactory
import com.hp.hpl.jena.query.QueryFactory
import com.hp.hpl.jena.query.QuerySolution
import com.hp.hpl.jena.query.ResultSet
//import java.net.URL
//import java.net.HttpURLConnection
import java.io._
//import java.io.OutputStreamWriter
//import java.io.BufferedReader
//import java.io.InputStreamReader
import scala.util.control.Breaks._
import scala.util.matching.Regex
import com.hp.hpl.jena.tdb.TDBFactory

class ComplementTypes {
  def createModel(aDirectory: String): Model = {
    val dataset = TDBFactory.createDataset(aDirectory)
    dataset.getDefaultModel();
  }

  def createNTFileIterator(aFile: String): StmtIterator = {
    val input = FileManager.get().open(aFile)
    val model = ModelFactory.createDefaultModel()
    model.read(input, null, "N-TRIPLES")
    model.listStatements()
  }

  // Builds the default query to be used in the select command
  def buildQuery(aString: String): String = {
    "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>" + '\n' +
      "SELECT ?o " + '\n' +
      "WHERE {<" + aString + "> rdf:type ?o}"
  }

  // Executes a select query over the datasets
  def executeQuery(aQuery: String, aModel: Model): ResultSet = {
    val query = QueryFactory.create(aQuery)
    val qexec = QueryExecutionFactory.create(query, aModel)
    qexec.execSelect()
  }

  // Utility function to append the final string to the initial instance types triples file
  def using[A <: {def close(): Unit}, B](param: A)(f: A => B): B =
    try { f(param) } finally { param.close() }

  def appendToFile(fileName:String, textData:String) =
    using (new FileWriter(fileName, true)){
      fileWriter => using (new PrintWriter(fileWriter)) {
        printWriter => printWriter.println(textData)
      }
    }
}

object ComplementTypes {
  val aTypeManager = new ComplementTypes()

  def main(args: Array[String]) {
    val initialTypesFile = args(0)
    val complementTypesFile = args(1)
    val langToLangLinksFile = args(2)
    val tdbStoreInitialLang = args(3)
    val tdbStoreCompLang = args(4)
    val outputPath = args(5)

    //////////////////////////////////////////////////////////////////////////////////////////////
    // Iterator using the bijective inter-language links triples file path as input. We will iterate through
    // all the lines in this file
    val it = aTypeManager.createNTFileIterator(langToLangLinksFile)

    // Create models so we can query the instance types files from both languages

    // Creates the dataset and loads the model for the initial instance types file
    val tdb1 = aTypeManager.createModel(tdbStoreInitialLang)
    FileManager.get().readModel( tdb1, initialTypesFile, "N-TRIPLES" )

    // Creates the model for the complement language instance types file
    val tdb2 = aTypeManager.createModel(tdbStoreCompLang)
    FileManager.get().readModel( tdb2, complementTypesFile, "N-TRIPLES" )

    while (it.hasNext()) {
      val stmt = it.nextStatement()
      val subject = stmt.getSubject().toString()
      val obj = stmt.getObject().toString()

      // Displays the corresponding entity in another language.
      //System.out.println(obj)

      // The object from a bijective inter-language links triples file can be used as input here. The idea
      // is to query the instance types file related to the object in order to complement the instance types file
      // related to the subject. For instance, if in portuguese a subject has no types associated to it, we can
      // use other languages to find dbpedia types for it
      val newSubject = obj

      //////////////////////////////////////////////////////////////////////////////////////////////

      // A query to find if the subject has any types in the instance types triples file
      val occQuery = aTypeManager.buildQuery(subject)
      val results1 = aTypeManager.executeQuery(occQuery, tdb1)

      // A query to find the types of a subject in the instance types triples file
      val typesQuery = aTypeManager.buildQuery(newSubject)
      val results2 = aTypeManager.executeQuery(typesQuery, tdb2)

      //TODO, if the subject has at least one type, search for new types in the object instance types file and add them accordingly
      /*if (results1.hasNext()) {
        while (results1.hasNext()) {
          var soln = results1.nextSolution()
          var name = soln.get("o")
          var finalString = "<" + subject + "> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> "
          finalString += "<" + name + "> "

          // Displays the types for the current entity. These types are the ones already present in the initial instance
          // types languange triples file for the current entity.
          //System.out.println(finalString)
        }
      } else {*/
      if (!results1.hasNext()) {

        // Make a pattern so we can replace the owl occurrences
        val pattern = new Regex("(O|o)wl")

        // Iterating the ResultSet to get all its elements
        while (results2.hasNext()) {

          // <subject> <predicate> <object>
          var finalString = "<" + subject + "> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> "
          var soln = results2.nextSolution()

          if ((pattern findFirstMatchIn(soln.toString())) != None) {
            finalString += "<http://www.w3.org/2002/07/owl#Thing> "
          } else {
            var name = soln.get("o")
            finalString += "<" + name + "> "
          }

          // Displays on the screen the final string to be added to the subject
          // instance types triples file
          //System.out.println(finalString);

          // We can now concatenate the final string to the current instance types file
          aTypeManager.appendToFile(outputPath, finalString)

        }
      }
    }

    // Close the datasets now that we are done
    tdb1.close()
    tdb2.close()
  }
}