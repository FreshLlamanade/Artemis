package de.tum.in.www1.artemis.service.util;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.util.StreamUtils;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class XmlFileUtils {

    private static final Logger log = LoggerFactory.getLogger(XmlFileUtils.class);

    public static Document readFromString(String xmlString) {
        return parseDocument(xmlString);
    }

    /**
     * Reads an XMl file from the resources for a given relative path. Also replaces all Strings in the given file
     * based on the map parameter. Meaning key in the map -> replaced by mapped value
     *
     * @param resource The XML file resource with the data properly formatted
     * @param variablesToReplace A map containing key, that should get replaced by their mapped values
     * @return The parsed XML document with the replaced values
     */
    public static Document readXmlFile(Resource resource, @Nullable Map<String, String> variablesToReplace) {
        try {
            var configXmlText = StreamUtils.copyToString(resource.getInputStream(), Charset.defaultCharset());
            if (variablesToReplace != null) {
                for (final var replacement : variablesToReplace.entrySet()) {
                    configXmlText = configXmlText.replace(replacement.getKey(), replacement.getValue());
                }
            }

            return parseDocument(configXmlText);
        }
        catch (IOException e) {
            final var errorMessage = "Error loading template Jenins build XML: " + e.getMessage();
            log.error(errorMessage, e);
            throw new IllegalStateException(errorMessage, e);
        }
    }

    private static Document parseDocument(String configXmlText) {
        try {
            final var domFactory = DocumentBuilderFactory.newInstance();
            final var builder = domFactory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(configXmlText)));
        }
        catch (IOException | ParserConfigurationException | SAXException e) {
            final var errorMessage = "Error loading template Jenkins build XML: " + e.getMessage();
            log.error(errorMessage, e);
            throw new IllegalStateException(errorMessage, e);
        }
    }

    /**
     * Converts the xml document to a string.
     * @param document the xml document
     * @return string representation of the xml document
     * @throws TransformerException in case of errors
     */
    public static String writeToString(Document document) throws TransformerException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        StringWriter stringWriter = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(stringWriter));
        return stringWriter.toString();
    }
}
