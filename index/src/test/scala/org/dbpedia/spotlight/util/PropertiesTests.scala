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

import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

import com.typesafe.config.ConfigFactory

/**
 * Tests if the properties file has the needed configuration
 *
 * @author reinaldo
 */
class PropertiesTests() {

  val config = ConfigFactory.load

  @Before
  def checkInputFile() {
    assertTrue(config.hasPath("dataSet.inputFile"))
    assertTrue(config.hasPath("dataSet.location"))
    assertTrue(config.hasPath("execution.extraction"))
    assertTrue(config.hasPath("execution.outputFormat"))
    assertTrue(config.hasPath("execution.inputFile"))
    assertTrue(config.hasPath("execution.outputFile"))
  }

  @Test
  def checkPossibleValues() {
    assertTrue(
      "object".equals(
         config.getString("execution.extraction")) || 
         "property".equals(config.getString("execution.extraction")
       )   
    )
         
    assertTrue(
      "TSV".equals(
        config.getString("execution.outputFormat")) || 
        "JSON".equals(config.getString("execution.outputFormat")
        )
      )
  }
}
