package org.anodyneos.xp;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.NamespaceSupport;

/**
 * XpContentHandler wraps a SAX ContentHandler and adds XP specific support. The
 * following features are provided:
 *
 * 1. Attributes may be set using XpContentHandler any time after startElement
 * is called, but before any other node is added.
 *
 * 2. setNamespacePrefixes() controls the passing of xmlns attributes to the
 * wrapped content handler. This will normally be set by an XMLReader when the
 * feature "http://xml.org/sax/features/namespaces" is set. This class will both
 * filter attributes that are passed to it and generate new attributes as
 * necessary. The default value is false (same as XMLReader's default.)
 *
 * 3. Start and end prefix mapping calls to the wrapped ContentHandler are
 * managed by this class. Calls to startPrefixMapping() may be made at any time
 * and will take effect when the next call to startElement is made. Calls to
 * endPrefixMapping() are ignored - this class will track mappings and make the
 * necessary calls to the wrapped ContentHandler as necessary.
 *
 * 4. Convenience methods such as characters(String s).
 *
 * 5. Tracking of prefix to namespace URI mappings.
 *
 * @author John Vasileff
 */
public final class XpContentHandler implements ContentHandler {

    private static final Log logger = LogFactory.getLog(XpContentHandler.class);

    // instance variables to test for logging for performance.
    private boolean logDebugEnabled = logger.isDebugEnabled();

    /**
     * current setting for the SAX feature "http://xml.org/sax/features/namespace-prefixes".
     */
    private boolean namespacePrefixes = false;

    /**
     * tracks prefix to namespace URI mappings.
     */
    private NamespaceSupport namespaceSupport = new NamespaceSupport();

    /**
     * holds values for the next element, set by startElement().  When flush() is called, these values
     * are passed to the wrapped ContentHandler's startElemement() methods and then set to null.
     */
    private String bufferedElLocalName;
    private String bufferedElQName;
    private String bufferedElNamespaceURI;

    /**
     * holds attributes relevent to the "nextEl".  When nextElLocalName is null this object will be empty.
     */
    private AttributesImpl bufferedElAttributes = new AttributesImpl();

    /**
     * holds values passed into startPrefixMappings().  When startElement() is called the contents are stored to
     * namespaceSupport.  flush() will ensure that startPrefixMappings()
     * calles are made on the wrapped contentHandler correctly.
     */
    private Map nextElStartPrefixMappings = new HashMap();

    private ContentHandler wrappedContentHandler;

    public XpContentHandler(ContentHandler contentHandler) {
        this.wrappedContentHandler = contentHandler;
    }

    ////////////////////////////////////////////////////////////////////////////////
    //
    // SAX Methods (managed)
    //
    ////////////////////////////////////////////////////////////////////////////////

    /**
     * startPrefixMapping() may be called at any time; the new mapping will take
     * effect for the next element provided to startElement().
     */
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        if(logDebugEnabled) {
            logger.debug("startPrefixMapping(\"" + prefix + "\", \"" + uri + "\") called.");
        }
        nextElStartPrefixMappings.put(prefix, uri);
    }

    public void startElement( String namespaceURI, String localName, String qName, Attributes atts)
    throws SAXException {
        if(logDebugEnabled) {
            logger.debug("startElement("
                    + namespaceURI
                    + ", " + localName
                    + ", " + qName
                    + ", " + atts
                    + ") called.");
        }
        flush();

        // buffer this element to allow attributes to be added
        bufferedElNamespaceURI = namespaceURI;
        bufferedElLocalName = localName;
        bufferedElQName = qName;
        bufferedElAttributes.clear();
        if (atts != null) {
            bufferedElAttributes.setAttributes(atts);
        }

        // nextEl is now bufferedEl, save mappings and get ready for mappings for the next element.
        namespaceSupport.pushContext();
        Iterator it = nextElStartPrefixMappings.keySet().iterator();
        while (it.hasNext()) {
            String prefix = (String) it.next();
            namespaceSupport.declarePrefix(prefix, (String) nextElStartPrefixMappings.get(prefix));
        }
        nextElStartPrefixMappings.clear();
    }

    public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
        if(logDebugEnabled) {
            logger.debug("endElement("
                    + namespaceURI
                    + ", " + localName
                    + ", " + qName
                    + ") called.");
        }
        flush();

        // call endElement on the wrappedContentHandler
        if(logDebugEnabled) {
            logger.debug("   calling wrapped contentHandler.endElement("
                    + namespaceURI + ", " + localName + ", " + qName + ").");
        }
        wrappedContentHandler.endElement(namespaceURI, localName, qName);

        // un-map prefix mappings.
        Enumeration e = namespaceSupport.getDeclaredPrefixes();
        while (e.hasMoreElements()) {
            String prefix = (String) e.nextElement();
            if(logDebugEnabled) {
                logger.debug("   calling wrapped contentHandler.endPrefixMapping('" + prefix + "').");
            }
            wrappedContentHandler.endPrefixMapping(prefix);
        }

        // pop namespace context
        namespaceSupport.popContext();
    }

    public void endPrefixMapping(String prefix) throws SAXException {
        if(logDebugEnabled) {
            logger.debug("endPrefixMapping(\"" + prefix + "\") called.");
        }
        // no op: we handle endPrefixMapping calls to the wrapped contentHandler automatically
    }

    ////////////////////////////////////////////////////////////////////////////////
    //
    // Convenience Methods (managed)
    //
    ////////////////////////////////////////////////////////////////////////////////

    /**
     * This method tries to be forgiving, the following rules apply:
     *
     * 1. When uri == null && qName has a prefix: The prefix must be in scope.
     * The namespace URI in the output will be that of the namespace associated
     * with the prefix. If the prefix is not in scope, a SAXException is thrown.
     *
     * 2. When uri == null && qName has no prefix: The qName is used as is with
     * no namespace URI.
     *
     * 3. When uri == "": The attribute will have no namespace and be output
     * without a prefix.
     *
     * 4. When uri == someURI: A prefix will be given to the attribute in the
     * following priority:
     *
     * 4.A) the provided prefix if one was provided and it is currently mapped to
     * the uri.
     *
     * 4.B) a prefix that is currently mapped to the URI.
     *
     * 4.C) the provided prefix if it is not currently mapped to another URI.
     *
     * 4.D) a generated prefix. In the case of C or D, a new namespace mapping
     * will be created.
     */
    public void addAttribute(final String uri, final String qName, final String value)
    throws SAXException {
        if (null == bufferedElLocalName) {
            throw new SAXException("Cannot addAttribute() unless directly after startElement().");
        } else {
            // lets put this code here, not in flush().  This way the internal state is kept current and we get
            // immediate feedback on errors.

            final String myQName;
            final String myURI;
            final String prefix = parsePrefix(qName);
            final String localName = parseLocalName(qName);

            if (localName.length() == 0) {
                throw new SAXException("Could not determine localName for attribute: '" + qName + "'.");
            }

            if (null == uri) {
                if (prefix.length() == 0) {
                    myQName = localName;
                    myURI = "";
                } else {
                    // the prefix must be in scope
                    myURI = namespaceSupport.getURI(prefix);
                    if (null == myURI) {
                        throw new SAXException("Cannot find URI for '" + qName + "' and none was provided.");
                    }
                    myQName = qName;
                }
            } else if (uri.length() == 0) {
                // use "" URI and no prefix
                myQName = localName;
                myURI = "";
            } else {
                // we have a uri, lets find a good prefix
                if (prefix.length() != 0 && uri.equals(namespaceSupport.getURI(prefix))) {
                    // Case A: prefix was provided and uri matches
                    myQName = qName;
                    myURI = uri;
                } else {
                    String p = namespaceSupport.getPrefix(uri);
                    if (null != p) {
                        // Case B: we already have a perfectly good prefix
                        myQName = p + ":" + localName;
                        myURI = uri;
                    } else if (prefix.length() != 0 && (null == namespaceSupport.getURI(prefix))) {
                        // Case C: the provided prefix will do; create new namespace mapping
                        namespaceSupport.declarePrefix(prefix, uri);
                        myQName = qName;
                        myURI = uri;
                    } else {
                        // Case D: punt... generate a new prefix for the attribute
                        p = genPrefix();
                        namespaceSupport.declarePrefix(p, uri);
                        myQName = p + ":" + localName;
                        myURI = uri;
                    }
                }
            }
            bufferedElAttributes.addAttribute(myURI, localName, myQName, "CDATA", value);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////
    //
    // SAX Methods (simple pass through)
    //
    ////////////////////////////////////////////////////////////////////////////////

    public void characters(char[] ch, int start, int length) throws SAXException {
        flush();
        if (ch != null) {
            wrappedContentHandler.characters(ch, start, length);
        }
    }

    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        flush();
        wrappedContentHandler.ignorableWhitespace(ch, start, length);
    }

    public void processingInstruction(String target, String data) throws SAXException {
        flush();
        wrappedContentHandler.processingInstruction(target, data);
    }

    public void skippedEntity(String name) throws SAXException {
        flush();
        wrappedContentHandler.skippedEntity(name);
    }

    public void setDocumentLocator(Locator locator) {
        wrappedContentHandler.setDocumentLocator(locator);
    }

    ////////////////////////////////////////////////////////////////////////////////
    //
    // Xp specific getters/setters
    //
    ////////////////////////////////////////////////////////////////////////////////

    /**
     * This method should be used carefully; output should not be made directly
     * to the wrapped <code>ContentHandler</code>.
     *
     * @return the wrapped <code>ContentHandler</code>
     */
    public ContentHandler getWrappedContentHandler() {
        return wrappedContentHandler;
    }

    public boolean isNamespacePrefixes() {
        return namespacePrefixes;
    }

    public void setNamespacePrefixes(boolean namespacePrefixes) {
        this.namespacePrefixes = namespacePrefixes;
    }

    ////////////////////////////////////////////////////////////////////////////////
    //
    // private utility methods
    //
    ////////////////////////////////////////////////////////////////////////////////

    private void flush() throws SAXException {
        // NOTE: to handle http://xml.org/sax/features/namespace-prefixes, we will first
        // remove all "xmlns" and xmlns:xxx" attributes, then add required attributes from namespaceSupport if the
        // feature is set to "true"

        for (int i = 0; i < bufferedElAttributes.getLength(); i++) {
            String qName = bufferedElAttributes.getQName(i);
            if (qName.equals("xmlns") || qName.startsWith("xmlns:")) {
                bufferedElAttributes.removeAttribute(i);
            }
        }

        if (null != bufferedElLocalName) {
            // start prefix mappings; namespaceSupport.push() was already called
            Enumeration e = namespaceSupport.getDeclaredPrefixes();
            while (e.hasMoreElements()) {
                String prefix = (String) e.nextElement();
                String uri = namespaceSupport.getURI(prefix);
                if(logDebugEnabled) {
                    logger.debug("   calling wrapped contentHandler.startPrefixMapping("
                            + "'"   + prefix + "'"
                            + ", '" + uri + "'"
                            + ").");
                }
                wrappedContentHandler.startPrefixMapping(prefix, uri);
                if (namespacePrefixes) {
                    String qName;
                    if (prefix.length() == 0) {
                        qName = "xmlns";
                    } else {
                        qName = "xmlns:" + prefix;
                    }
                    if(logDebugEnabled) {
                        logger.debug("   adding namespace-prefix attribute " + qName + "= '" + uri + "').");
                    }
                    bufferedElAttributes.addAttribute("", "", qName, "CDATA", uri);
                }
            }
            if(logDebugEnabled) {
                logger.debug("   calling wrapped contentHandler.startElement("
                        + "'"   + bufferedElNamespaceURI + "'"
                        + ", '" + bufferedElLocalName + "'"
                        + ", '" + bufferedElQName + "'"
                        + ", '" + bufferedElAttributes + "'"
                        + ").");
            }

            wrappedContentHandler.startElement(bufferedElNamespaceURI, bufferedElLocalName,
                    bufferedElQName, bufferedElAttributes);
            bufferedElNamespaceURI = null;
            bufferedElLocalName = null;
            bufferedElQName = null;
            bufferedElAttributes.clear();
        }
    }

    private static final String parsePrefix(String qName) {
        if (null == qName || qName.length() == 0) {
            return "";
        } else {
            int colon = qName.indexOf(':');
            if (-1 == colon) {
                return "";
            } else {
                return qName.substring(0, colon);
            }
        }
    }

    private static final String parseLocalName(String qName) {
        if (null == qName || qName.length() == 0) {
            return "";
        } else {
            int colon = qName.indexOf(':');
            if (-1 == colon) {
                return qName;
            } else {
                return qName.substring(colon + 1);
            }
        }
    }

    private int prefixNum = 0;
    private String genPrefix() {
        String prefix;
        do {
            // comment this out. Better to be repeatable.
            //prefix = "n" + Integer.toString((int) (Math.random() *
            // Integer.MAX_VALUE), 36);
            prefix = "n" + prefixNum++;
        } while (null != namespaceSupport.getURI(prefix));
        return prefix;
    }

    ////////////////////////////////////////////////////////////////////////////////
    //
    // characters(xxx) convenience methods
    //
    ////////////////////////////////////////////////////////////////////////////////

    public void characters(String s) throws SAXException {
        flush();
        if (null != s) {
            characters(s.toCharArray(), 0, s.length());
        }
    }

    public void characters(Object x) throws SAXException {
        if (null != x) {
            characters(x.toString());
        }
    }

    public void characters(char x) throws SAXException {
        characters(String.valueOf(x));
    }

    public void characters(byte x) throws SAXException {
        characters(String.valueOf(x));
    }

    public void characters(boolean x) throws SAXException {
        characters(String.valueOf(x));
    }

    public void characters(int x) throws SAXException {
        characters(String.valueOf(x));
    }

    public void characters(long x) throws SAXException {
        characters(String.valueOf(x));
    }

    public void characters(float x) throws SAXException {
        characters(String.valueOf(x));
    }

    public void characters(double x) throws SAXException {
        characters(String.valueOf(x));
    }

    public void endDocument() throws SAXException {
        // TODO should calls to this method be ignored?
    }

    public void startDocument() throws SAXException {
        // TODO should calls to this method be ignored?
    }

}
