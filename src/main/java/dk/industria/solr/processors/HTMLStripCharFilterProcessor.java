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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Collection;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import org.apache.lucene.analysis.CharReader;
import org.apache.lucene.analysis.CharStream;

import org.apache.solr.analysis.HTMLStripCharFilter;


import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;


import org.apache.solr.update.AddUpdateCommand;

import org.apache.solr.update.processor.UpdateRequestProcessor;

/**
 * Implements an UpdateRequestProcessor for running Solr HTMLStripCharFilter on
 * select document fields before they are stored.
 * <p/>
 *  In addition to running the HTMLStringCharFilter it also space normalizes the fields
 *  by removing no-break spaces, trimming leading and trailing spaces and finally
 *  replaces multiple recurring spaces with a single space.
 * <p/>
 * For more information on configuration @see HTMLStripCharFilterProcessorFactory
 */
class HTMLStripCharFilterProcessor extends UpdateRequestProcessor {
    /**
     * Logger
     * UpdateRequestProcessor has it's own log variable tied to the UpdateRequestProcessor class,
     * which makes controlling log output from this project difficult unless a different
     * logger is used as in this case.
     */
    private static final Logger logger = LoggerFactory.getLogger(HTMLStripCharFilterProcessor.class);
    /**
     * Size of the buffer used to read the input through the HTMLStripCharFilter.
     */
    private static final int BUFFER_SIZE = 4096;
    /**
     * List of fields to process with the HTMLStripCharFilter.
     */
    private final List<String> fieldsToProcess;
    /**
     * Indicates if field values should be space normalized after running the filter.
     */
    private final boolean spaceNormalize;
    /**
     * Space normalizes the string by changing no-break space into normal spaces,
     * trimming the string for leading and trailing spaces and finally removing
     * duplicate spaces from the string.
     *
     * @param text String to space normalize..
     * @return String with normalized spaces..
     */
    private String normalizeSpace(String text) {
        if (null == text) return "";
        // Replace no-break space
        String noBreakRemoved = text.replaceAll("\u00A0", " ");
        String trimmed = noBreakRemoved.trim();
        // Replace multiple recurring spaces with a single space
        return trimmed.replaceAll("\\p{Space}{2,}", " ");
    }

    /**
     * Strip HTML/XML from string by reading it through the Solr HTMLStripCharFilter.
     *
     * @param text String containing HTML/XML to be stripped.
     * @return String with HTML/XML removed.
     * @throws IOException  if reading the string through the HTMLStripCharFilter.
     */
    private String runHtmlStripCharFilter(String text) throws IOException {
        StringBuilder stripped = new StringBuilder();
        try {
            char[] buffer = new char[BUFFER_SIZE];

            Reader r = new StringReader(text);
            if (!r.markSupported()) {
                logger.debug("Reader returned false for mark support, wrapped in BufferedReader.");
                r = new BufferedReader(r);
            }

            CharStream cs = CharReader.get(r);
            HTMLStripCharFilter filter = new HTMLStripCharFilter(cs);
            while (true) {
                int nCharsRead = filter.read(buffer);
                if (-1 == nCharsRead) {
                    break;
                }
                if (0 < nCharsRead) {
                    stripped.append(buffer, 0, nCharsRead);
                }
            }
            filter.close();
        } catch (IOException e) {
            logger.error("IOException thrown in HTMLStripCharFilter: {}", e.toString());
            throw e;
        }
        return stripped.toString();
    }

    /**
     * Construct a HTMLStripCharFilterProcessor.
     *
     * @param fields List of field names to process.
     * @param spaceNormalize Set to true if field values should be space normalized.
     * @param next   Next UpdateRequestProcessor in the processor chain.
     */
    public HTMLStripCharFilterProcessor(final List<String> fields, final boolean spaceNormalize, final UpdateRequestProcessor next) {
        super(next);
        this.fieldsToProcess = fields;
        this.spaceNormalize = spaceNormalize;
    }

    /**
     * Called by the processor chain on document add/update operations.
     * This is where we process the fields configured before they are indexed.
     *
     * @param cmd AddUpdateCommand
     * @throws IOException
     */
    @Override
    public void processAdd(AddUpdateCommand cmd) throws IOException {
        SolrInputDocument doc = cmd.getSolrInputDocument();
        for (String fieldName : this.fieldsToProcess) {
            logger.debug("Processing field: {}", fieldName);

            SolrInputField field = doc.getField(fieldName);
            if (null == field) continue;

            Collection<Object> values = field.getValues();
            if (null == values) continue;

            Collection<Object> newValues = new ArrayList<Object>();
            for (Object value : values) {
                if (value instanceof String) {
                    String newValue = runHtmlStripCharFilter((String) value);
                    if(this.spaceNormalize) {
                        newValue = normalizeSpace(newValue);
                    }
                    newValues.add(newValue);
                } else {
                    newValues.add(value);
                }
            }
            float boost = field.getBoost();
            field.setValue(newValues, boost);
        }
        super.processAdd(cmd);
    }
}
