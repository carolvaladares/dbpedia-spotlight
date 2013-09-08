/*
 * Copyright 2012 DBpedia Spotlight Development Team
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  Check our project website for information on how to acknowledge the authors and how to contribute to the project: http://spotlight.dbpedia.org
 */

package org.dbpedia.spotlight.lucene.index

import java.io.File
import scala.collection.JavaConversions
import org.dbpedia.spotlight.io.FileOccurrenceSource
import org.apache.commons.logging.LogFactory
import org.apache.lucene.store.FSDirectory
import org.dbpedia.spotlight.lucene.LuceneManager
import org.dbpedia.spotlight.util.ConfigurationLoader
import org.dbpedia.spotlight.model.Factory
import scala.collection.JavaConverters._

/**
 * Indexes all occurrences of a DBpedia Resource in Wikipedia as a Lucene index where each document represents one resource.
 *
 * @author maxjakob
 */
object IndexMergedOccurrences
{
    private val LOG = LogFactory.getLog(this.getClass)

    def index(trainingInputFile : String, indexer: OccurrenceContextIndexer ) {
        val wpOccurrences = FileOccurrenceSource.fromFile(new File(trainingInputFile))
        var indexDisplay = 0
        LOG.info("Indexing with " + indexer.getClass + " in Lucene ...")

        wpOccurrences.foreach( occurrence => {
            try {

            indexer.add(occurrence)

            indexDisplay += 1
            if (indexDisplay % 10000 == 0) {
                LOG.debug("  indexed " + indexDisplay + " occurrences")
            }
            } catch {
                case e: Exception => {
                    LOG.error("Error parsing %s. ".format(indexDisplay))
                    e.printStackTrace()
                }
            }
        })
        indexer.close()  // important

        LOG.info("Finished: indexed " + indexDisplay + " occurrences")
    }

    def getBaseDir(baseDirName : String) : String = {
        if (! new File(baseDirName).exists) {
            println("Base directory not found! "+baseDirName)
            System.exit(1)
        }
        baseDirName
    }

    /**
     *
     * Usage: mvn scala:run -DmainClass=org.dbpedia.spotlight.lucene.index.IndexMergedOccurrences "-DaddArgs=output/occs.uriSorted.tsv|overwrite"
     */
    def main(args : Array[String])
    {
      // Creates an empty property list
      val config = new ConfigurationLoader()
      val trainingInputFileName = args(0)

      var shouldOverwrite = false
      if (args.length>1) {
          if (args(1).toLowerCase.contains("overwrite"))
              shouldOverwrite = true
      }

      // Command line options
      val baseDir = config.properties.getProperty("org.dbpedia.spotlight.index.dir")
      val similarity = Factory.Similarity.fromName("InvCandFreqSimilarity")
      val stopWords = setAsJavaSetConverter(config.properties.getProperty("org.dbpedia.spotlight.data.stopWords").toSet).asJava.asInstanceOf[java.util.Set[String]]
      val analyzer = Factory.Analyzer.from(config.properties.getProperty("org.dbpedia.spotlight.lucene.analyzer"), config.properties.getProperty("org.dbpedia.spotlight.lucene.version"), stopWords)

      LOG.info("Using dataset under: "+baseDir)
      LOG.info("Similarity class: "+similarity.getClass)
      LOG.info("Analyzer class: "+analyzer.getClass)

      LOG.warn("WARNING: this process will run a lot faster if the occurrences are sorted by URI!")

      val minNumDocsBeforeFlush : Int = config.properties.get("org.dbpedia.spotlight.index.minDocsBeforeFlush", "200000").asInstanceOf[Int]
      val lastOptimize = false

      //val indexOutputDir = baseDir+"2.9.3/Index.wikipediaTraining.Merged."+analyzer.getClass.getSimpleName+"."+similarity.getClass.getSimpleName;
      val indexOutputDir = baseDir

      val lucene = new LuceneManager.BufferedMerging(FSDirectory.open(new File(indexOutputDir)),
                                                      minNumDocsBeforeFlush,
                                                      lastOptimize)

      println("Before context similarity.")
      lucene.setContextSimilarity(similarity)
      println("Before analyzer.")
      lucene.setDefaultAnalyzer(analyzer)
      // If the index directory does not exist, tell lucene to overwrite.
      // If it exists, the user has to indicate in command line that he/she wants to overwrite it.
      // I chose command line instead of configuration file to force the user to look at it before running the command.
      if (!new File(indexOutputDir).exists()) {
          lucene.shouldOverwrite = true
          new File(indexOutputDir).mkdir()
      } else {
          lucene.shouldOverwrite = shouldOverwrite
      }

      val vectorBuilder = new MergedOccurrencesContextIndexer(lucene)
      val freeMemGB : Double = Runtime.getRuntime.freeMemory / 1073741824.0
      if (Runtime.getRuntime.freeMemory < minNumDocsBeforeFlush) LOG.error("Your available memory "+freeMemGB+"GB is less than minNumDocsBeforeFlush. This setting is known to give OutOfMemoryError.");
      LOG.info("Available memory: "+freeMemGB+"GB")
      LOG.info("Max memory: "+Runtime.getRuntime.maxMemory / 1073741824.0 +"GB")
      /* Total memory currently in use by the JVM */
      LOG.info("Total memory (bytes): " + Runtime.getRuntime.totalMemory / 1073741824.0 + "GB")
      //LOG.info("MinNumDocsBeforeFlush: "+minNumDocsBeforeFlush)

      index(trainingInputFileName, vectorBuilder)

      LOG.info("Index saved to: "+indexOutputDir )
        
    }
}
