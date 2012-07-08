/**
 * Copyright 2011 James Lindstorff
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
 */
package dk.industria.solr.processors;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.update.AddUpdateCommand;
import org.apache.solr.update.processor.UpdateRequestProcessor;


import scala.collection.JavaConverters._

/**
 * <p>Implements an UpdateRequestProcessor for filtering documents based on allow/disallow rules
 * matching regular expressions in field values.
 * <p/>
 * For more information @see AllowDisallowIndexingProcessorFactory
 * @param mode      AllowDisallowMode indicating the mode of operation.
 * @param rules     List of field match rule.
 * @param uniqueKey Name of the document unique key. Null is no unique key is defined in the schema.
 * @param next      Next UpdateRequestProcessor in the processor chain.
 */
class AllowDisallowIndexingProcessor(mode: AllowDisallowMode.Value, rules: List[FieldMatchRule], uniqueKey: String, next: UpdateRequestProcessor) extends UpdateRequestProcessor(next) {
    /**
     * Logger
     * UpdateRequestProcessor has it's own log variable tied to the UpdateRequestProcessor class,
     * which makes controlling log output from this project difficult unless a different
     * logger is used as in this case.
     */
    private val logger = LoggerFactory.getLogger(getClass())

    /**
     * Indicates if running the rules results on a match in the document.
     *
     * @param rules    List of field match rules to run against the document.
     * @param document SolrInputDocument to run rules against.
     * @return True if one of the rules matched the document.
     */
    private def rulesMatch(rules: List[FieldMatchRule], document: SolrInputDocument): Boolean = {
      for (rule <- rules.asScala) {
        logger.debug("Testing rule: {}", String.valueOf(rule))
	
        val ruleField = rule.field
        val fieldValues = document.getFieldValues(ruleField)
        for (objectValue <- fieldValues.asScala) {
          if (objectValue.isInstanceOf[String]) {
            val value = objectValue.asInstanceOf[String]
            if (rule.matches(value)) {
              logger.debug("Matched rule [{}] on value [{}]", String.valueOf(rule), value)
              return true
            }
          }
        }
      }
      // No rules matched
      return false
    }

    /**
     * Get the value of the documents unique key.
     *
     * @param document SolrInputDocument to get the value from.
     * @return String representation of the documents unique value key.
     */
    private def uniqueKeyValue(document: SolrInputDocument): String = {
        if (null == this.uniqueKey) return ""

        val value = document.getFieldValue(this.uniqueKey)
        return String.valueOf(value);
    }

    /**
     * Called by the processor chain on document add/update operations.
     * This is where we check the allow / disallow rules.
     *
     * @param cmd AddUpdateCommand
     * @throws IOException
     */
    @throws(classOf[IOException])
    override def processAdd(cmd: AddUpdateCommand) = {
      if (this.mode == AllowDisallowMode.Unknown) {
        logger.warn("Mode UNKNOWN, indexing, check configuration!")
        super.processAdd(cmd)
      } else {
        val document = cmd.getSolrInputDocument()
        val ruleMatch = rulesMatch(this.rules, document)
	
        if ((this.mode == AllowDisallowMode.Allow) && (!ruleMatch)) {
          logger.info("DocId [{}] discarded - allow mode without rule match", uniqueKeyValue(document))
        } else if ((this.mode == AllowDisallowMode.Disallow) && (ruleMatch)) {
          logger.info("DocId [{}] discarded - disallow mode with rule match", uniqueKeyValue(document))
        } else {
          logger.info("DocId [{}] indexing", uniqueKeyValue(document))
          super.processAdd(cmd)
	}
      }
    }

}
