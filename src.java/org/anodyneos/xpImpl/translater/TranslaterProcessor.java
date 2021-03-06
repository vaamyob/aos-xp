package org.anodyneos.xpImpl.translater;


import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.anodyneos.commons.xml.sax.ElementProcessor;
import org.anodyneos.xpImpl.util.CodeWriter;
import org.anodyneos.xpImpl.util.Util;
import org.xml.sax.SAXException;

class TranslaterProcessor extends ElementProcessor {

    public static final String URI_XP = "http://www.anodyneos.org/xmlns/xp";
    public static final String URI_XP_TAG = "http://www.anodyneos.org/xmlns/xptag";
    public static final String URI_NAMESPACES = "http://www.w3.org/2000/xmlns/";

    private TranslaterContext ctx;
    private Map bufferedStartPrefixMappings = new HashMap();

    public TranslaterProcessor(TranslaterContext ctx) {
        super(ctx);
        this.ctx = ctx;
    }

    protected TranslaterContext getTranslaterContext() {
        return ctx;
    }

    public final void startPrefixMapping(java.lang.String prefix, java.lang.String uri) throws SAXException {
        /*
        CodeWriter out = getTranslaterContext().getCodeWriter();
        out.printIndent().println(
              "xpCH.startPrefixMapping("
            + "\""   + prefix + "\""
            + ",\""  + uri + "\""
            + ");"
        );
        */
        ctx.bufferStartPrefixMapping(prefix, uri);
    }

    // XpContentHandler takes care of this for us...
    /*
    public void endPrefixMapping(java.lang.String prefix) throws SAXException {
        CodeWriter out = getTranslaterContext().getCodeWriter();
        out.printIndent().println(
              "xpCH.endPrefixMapping("
            + "\""   + prefix + "\""
            + ");"
        );
    }
    */

    public int outputBufferedMappingsAsPhantoms() {
        int count = 0;
        CodeWriter out = ctx.getCodeWriter();
        Map prefixMappings = ctx.getBufferedStartPrefixMappings();
        for (Iterator it = prefixMappings.keySet().iterator(); it.hasNext();) {
            String prefix = (String) it.next();
            String url = (String) prefixMappings.get(prefix);
            out.printIndent().println(
                      "xpCH.pushPhantomPrefixMapping("
                    +       Util.escapeStringQuoted(prefix)
                    + "," + Util.escapeStringQuoted(url)
                    + ");");
            count++;
        }
        ctx.clearBufferedStartPrefixMappings();
        return count;
    }
}
