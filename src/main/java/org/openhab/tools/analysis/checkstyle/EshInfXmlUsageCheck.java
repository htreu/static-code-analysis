/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.tools.analysis.checkstyle;

import java.io.File;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;

import org.openhab.tools.analysis.checkstyle.api.AbstractEshInfXmlCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.puppycrawl.tools.checkstyle.api.CheckstyleException;

/**
 * Check for missing bridge-type or supported bridge-type-refs in the same file.<br>
 * Check for missing config file if there is a uri reference to configuration.
 *
 * @author Svlien Valkanov - Initial implementation
 *
 */
public class EshInfXmlUsageCheck extends AbstractEshInfXmlCheck {
    private static final String CONFIG_DESCRIPTION_EXPRESSION = "//config-description[@uri]/@uri";
    private static final String CONFIG_DESCRIPTION_REF_EXPRESSION = "//config-description-ref[@uri]/@uri";
    private static final String BRIDGE_TYPE_EXPRESSION = "//bridge-type[@id]/@id";
    private static final String SUPPORTED_BRIDGE_TYPE_REF_EXPRESSION = "//supported-bridge-type-refs/bridge-type-ref[@id]/@id";

    private static final String MESSAGE_MISSING_URI_CONFIGURATION = "Missing configuration for the configuration reference with uri - {0}";
    private static final String MESSAGE_MISSING_SUPPORTED_BRIDGE = "Missing the supported bridge with id {0}";
    private static final String MESSAGE_UNUSED_URI_CONFIGURATION = "Unused configuration reference with uri - {0}";
    private static final String MESSAGE_UNUSED_BRIDGE = "Unused bridge reference with id - {0}";

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private Map<String, File> allConfigDescriptionRefs = new HashMap<>();
    private Map<String, File> allConfigDescriptions = new HashMap<>();

    private Map<String, File> allSupportedBridges = new HashMap<>();
    private Map<String, File> allBridgeTypes = new HashMap<>();

    @Override
    public void finishProcessing() {
        // Check for missing supported bridge-type-refs.
        Map<String, File> missingSupportedBridges = removeAll(allSupportedBridges, allBridgeTypes);
        logMissingEntries(missingSupportedBridges, MESSAGE_MISSING_SUPPORTED_BRIDGE);

        // Check for missing referenced config descriptions
        Map<String, File> missingConfigDescriptions = removeAll(allConfigDescriptionRefs, allConfigDescriptions);
        logMissingEntries(missingConfigDescriptions, MESSAGE_MISSING_URI_CONFIGURATION);

        // Check for unused bridge-type-refs.
        Map<String, File> unusedBridges = removeAll(allBridgeTypes, allSupportedBridges);
        logMissingEntries(unusedBridges, MESSAGE_UNUSED_BRIDGE);

        // Check for unused referenced config descriptions
        Map<String, File> unusedConfigDescriptions = removeAll(allConfigDescriptions, allConfigDescriptionRefs);
        logMissingEntries(unusedConfigDescriptions, MESSAGE_UNUSED_URI_CONFIGURATION);
    }

    @Override
    protected void checkConfigFile(File xmlFile) throws CheckstyleException {
        // The allowed values are described in the config description XSD
        allConfigDescriptions.putAll(evaluateExpressionOnFile(xmlFile, CONFIG_DESCRIPTION_EXPRESSION));
    }

    @Override
    protected void checkBindingFile(File xmlFile) throws CheckstyleException {
        // The allowed values are described in the binding XSD
        allConfigDescriptionRefs.putAll(evaluateExpressionOnFile(xmlFile, CONFIG_DESCRIPTION_REF_EXPRESSION));
        allConfigDescriptions.putAll(evaluateExpressionOnFile(xmlFile, CONFIG_DESCRIPTION_EXPRESSION));
    }

    @Override
    protected void checkThingTypeFile(File xmlFile) throws CheckstyleException {
        // Process the files for all nodes below,
        // the allowed values are described in the thing description XSD
        allSupportedBridges.putAll(evaluateExpressionOnFile(xmlFile, SUPPORTED_BRIDGE_TYPE_REF_EXPRESSION));
        allBridgeTypes.putAll(evaluateExpressionOnFile(xmlFile, BRIDGE_TYPE_EXPRESSION));
        allConfigDescriptionRefs.putAll(evaluateExpressionOnFile(xmlFile, CONFIG_DESCRIPTION_REF_EXPRESSION));
        allConfigDescriptions.putAll(evaluateExpressionOnFile(xmlFile, CONFIG_DESCRIPTION_EXPRESSION));
    }

    private Map<String, File> evaluateExpressionOnFile(File xmlFile, String xPathExpression)
            throws CheckstyleException {
        Map<String, File> collection = new HashMap<>();
        NodeList nodes = getNodes(xmlFile, xPathExpression);

        if (nodes != null) {
            for (int i = 0; i < nodes.getLength(); i++) {
                collection.put(nodes.item(i).getNodeValue(), xmlFile);
            }
        }
        return collection;
    }

    private NodeList getNodes(File xmlFile, String expression) throws CheckstyleException {
        Document document = parseDomDocumentFromFile(xmlFile);

        XPathExpression xpathExpression = compileXPathExpression(expression);

        NodeList nodes = null;
        try {
            nodes = (NodeList) xpathExpression.evaluate(document, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            logger.error("Problem occurred while evaluating the expression {} on the {} file.", expression,
                    xmlFile.getName(), e);
        }
        return nodes;
    }

    private <K, V> Map<K, V> removeAll(Map<K, V> firstMap, Map<K, V> secondMap) {
        Map<K, V> result = new HashMap<>(firstMap);
        result.keySet().removeAll(secondMap.keySet());
        return result;
    }

    private <K> void logMissingEntries(Map<K, File> collection, String message) {
        for (K element : collection.keySet()) {
            File xmlFile = collection.get(element);
            logMessage(xmlFile.getPath(), 0, xmlFile.getName(), MessageFormat.format(message, element));
        }
    }
}
