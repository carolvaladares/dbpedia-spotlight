package org.dbpedia.spotlight.lucene.index

/**
 * Created with IntelliJ IDEA.
 * User: Renan
 * Date: 02/08/13
 * Time: 16:57
 * To change this template use File | Settings | File Templates.
 */

//import org.dbpedia.spotlight.util.{IndexingConfiguration, TypesLoader}
import com.hp.hpl.jena.rdf.model.ModelFactory
import com.hp.hpl.jena.rdf.model.Model
import com.hp.hpl.jena.rdf.model.StmtIterator
import com.hp.hpl.jena.util.FileManager
import com.hp.hpl.jena.query.QueryExecutionFactory
import com.hp.hpl.jena.query.QueryFactory
import com.hp.hpl.jena.query.ResultSet
import java.io._
import scala.util.matching.Regex
import com.hp.hpl.jena.tdb.TDBFactory
import java.util.Properties
import org.dbpedia.spotlight.exceptions.ConfigurationException
import org.apache.commons.logging.LogFactory
import org.apache.commons.logging.Log

class ComplementTypes {
  def testArrayLength(anArrayLength: Int, aLog: Log) {
    if (anArrayLength < 1) {
      aLog.error("At least one language must be supplied to execute this process.")
      System.exit(1)
    }
  }

  def createModel(aDirectory: String): Model = {
    val dataset = TDBFactory.createDataset(aDirectory)
    dataset.getDefaultModel
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
  private val LOG = LogFactory.getLog(this.getClass)
  val aTypeManager = new ComplementTypes()

  def main(args: Array[String]) {

    // Creates an empty property list
    val config: Properties = new Properties()

    try {
      config.load(new FileInputStream(new File("conf/indexing.properties")))
    }
    catch {
      case e: IOException => {
        throw new ConfigurationException("Cannot find configuration file " + "conf/indexing.properties", e)
      }
    }

    // Read in from the indexing.properties files all the data we need. Creates arrays accordingly
    val mainLanguage = Array(config.getProperty("org.dbpedia.spotlight.language_i18n_code", ""))
    val compLangsArray = config.getProperty("org.dbpedia.spotlight.complement_languages", "").split(",").toArray
    val allLangsArray = mainLanguage ++ compLangsArray

    // Checks if the number of languages is not valid
    aTypeManager.testArrayLength(allLangsArray.length, LOG)

    // Get the base directories used in this process. The user can set the paths to them in the indexing.properties file.
    // If the download.sh script was executed with the complement types option, all the needed directories were already created
    // and are defined inside the script.
    // TODO: make the download.sh script use directories arguments from the indexing.properties file, centralizing this process. ALso change the comment above accordingly
    val tdbStoreBaseDir = config.getProperty("org.dbpedia.spotlight.data.tdbStoreBaseDir","")
    val dbpediaBaseDir = config.getProperty("org.dbpedia.spotlight.data.dbpediaBaseDir","")
    val outputBaseDir = config.getProperty("org.dbpedia.spotlight.data.outputBaseDir","")

    // Creates an array to hold all the names of the instance types files we are going to need
    val instTypesNamesArray = new Array[String](allLangsArray.length)
    // Creates an array to hold all models so we can query the instance types files from all the required languages
    val tdbStoreArray = new Array[Model](allLangsArray.length)
    // Creates an iterator array so we can check every resource of the main language related to each complement language
    val fileIteratorArray = new Array[StmtIterator](allLangsArray.length-1)

    // Checks if the number of files to iterate through is not valid
    aTypeManager.testArrayLength(fileIteratorArray.length, LOG)

    var i = 0
    for( langName <- allLangsArray ){
      instTypesNamesArray(i) = "instance_types_" + langName + ".nt"
      println(instTypesNamesArray(i))
      println(tdbStoreBaseDir + langName + "/TDB")
      println(dbpediaBaseDir + langName + '/' + instTypesNamesArray(i))
      tdbStoreArray(i) = aTypeManager.createModel(tdbStoreBaseDir + langName + "/TDB")
      FileManager.get().readModel( tdbStoreArray(i), dbpediaBaseDir + langName + '/' + instTypesNamesArray(i), "N-TRIPLES" )
      if (i > 0) {
        println(outputBaseDir + mainLanguage(0) + '/' + allLangsArray(0) + '_' + langName + '_' + "links.nt")
        fileIteratorArray(i-1) = aTypeManager.createNTFileIterator(outputBaseDir + mainLanguage(0) + '/' + allLangsArray(0) + '_' + langName + '_' + "links.nt")
      }
      i += 1
    }

    //////////////////////////////////////////////////////////////////////////////////////////////

    // Now for each bijective inter languages links file we proceed with the core routine of the language types complement.
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
        val occsQuery = aTypeManager.buildQuery(subject)
        val occsResults = aTypeManager.executeQuery(occsQuery, tdbStoreArray(0))

        // A query to find the types of a subject in the instance types triples file
        val newTypesQuery = aTypeManager.buildQuery(newSubject)
        val newTypesResult = aTypeManager.executeQuery(newTypesQuery, tdbStoreArray(i))

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

            if ((pattern findFirstMatchIn soln.toString) != None) {
              finalString += "<http://www.w3.org/2002/07/owl#Thing> "
            } else {
              val name = soln.get("o")
              finalString += "<" + name + "> "
            }

            // Displays on the screen the final string to be added to the subject
            // instance types triples file
            //System.out.println(finalString)

            // We can now concatenate the final string to the current instance types file
            aTypeManager.appendToFile(outputBaseDir + instTypesNamesArray(0), finalString)
          }
        }
      }
    }

    // Close the datasets now that we are done
    i = 0
    for( langName <- allLangsArray ){
      tdbStoreArray(i).close()
      i += 1
    }
  }
}