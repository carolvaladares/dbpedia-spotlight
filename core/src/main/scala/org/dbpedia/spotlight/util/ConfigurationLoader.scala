
package org.dbpedia.spotlight.util

import java.io.FileInputStream
import java.util.Properties

class ConfigurationLoader() {
  val confFilePath = "C:/Users/Renan/Documents/GitHub/dbpedia-spotlight/conf/indexing.properties"
  val properties: Properties = new Properties()
  loadConfiguration(properties)

  def loadConfigurationFile(x: Properties) = {
    x.load(new FileInputStream(new java.io.File(confFilePath))) //../conf/indexing.properties")))
  }

  def loadConfiguration(aConfig: Properties) = {
    aConfig match {
      case x: Properties => {
        loadConfigurationFile(aConfig)
      }
      case _ => {
        throw new Exception("Cannot find the configuration file conf/indexing.properties.")
      }
    }
  }
}