package org.anodyneos.xpImpl.translater;

import org.anodyneos.commons.xml.sax.ElementProcessor;
import org.anodyneos.xpImpl.util.CodeWriter;
import org.anodyneos.xpImpl.util.JavaClass;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * ProcessorPage handles the xp:page element.
 *
 * <pre>
 *  <xp:page>
 *      <xp:output/>
 *      <xp:xsl/>...
 *      <xp:content/>
 *  </xp:page>
 * </pre>
 *
 * @author jvas
 */
class ProcessorPage extends TranslaterProcessor {

    private JavaClass jc = new JavaClass();

    //private OutputProcessor outputProcessor;
    //private ArrayList xmlPiplineProcessors = new ArrayList();
    private ProcessorContent contentProcessor;

    //static final String E_OUTPUT = "output";
    //static final String E_XML_PIPELINE = "xml-pipeline";
    public static final String E_CONTENT = "content";

    public ProcessorPage(TranslaterContext ctx) {
        super(ctx);
        contentProcessor = new ProcessorContent(ctx);
    }

    public ElementProcessor getProcessorFor(String uri, String localName, String qName) throws SAXException {
        if (URI_XP.equals(uri)) {
            if (E_CONTENT.equals(localName)) {
                return contentProcessor;
            } else {
                return super.getProcessorFor(uri, localName, qName);
            }
        } else {
            return super.getProcessorFor(uri, localName, qName);
        }
    }

    public void startElement(String uri, String localName, String qName,
            Attributes attributes) throws SAXException {
        printJavaHeader();
    }

    public void endElement(String uri, String localName, String qName) throws SAXException {
        printJavaFooter();
    }

    private void printJavaHeader() {
        // package, imports, class header
        JavaClass c = new JavaClass();
        c.setFullClassName(getTranslaterContext().getFullClassName());
        c.setExtends("org.anodyneos.xp.standalone.StandaloneXpPage");

        CodeWriter out = getTranslaterContext().getCodeWriter();
        c.printHeader(out);

        // constructor()
        out.printIndent().println("public " + c.getClassName() + "() {");
        out.indentPlus();
        out.printIndent().println("// super();");
        out.endBlock();
        out.println();

        // main()
        out.printIndent().println("public static void main(String[] args) throws Exception {");
        out.indentPlus();
        out.printIndent().println("long start;");
        out.printIndent().println("start = System.currentTimeMillis();");
        out.printIndent().println(c.getFullClassName() + " obj = new " + c.getFullClassName() + "();");
        out.printIndent().println("org.anodyneos.xp.util.XMLStreamer.process(new org.anodyneos.xp.standalone.StandaloneXpXMLReader(obj), System.out);");
        out.printIndent().println("System.out.println(\"Completed in \" + (System.currentTimeMillis() - start) + \" milliseconds\");");
        out.endBlock();
        out.println();
    }

    private void printJavaFooter() {
        CodeWriter out = getTranslaterContext().getCodeWriter();

        if(getTranslaterContext().getFragmentCount() > 0) {
            // Output Fragments
            out.flush();
            out.printIndent().println("private final class FragmentHelper implements org.anodyneos.xp.tagext.XpFragment {");
            out.indentPlus();
            out.println();
            out.printIndent().println("private int fragNum;");
            out.printIndent().println("private org.anodyneos.xp.XpContext xpContext;");
            out.printIndent().println("private org.anodyneos.xp.tagext.XpTag xpTagParent;");
            out.println();
            out.printIndent().println("public FragmentHelper(int fragNum, org.anodyneos.xp.XpContext xpContext, org.anodyneos.xp.tagext.XpTag xpTagParent) {");
            out.indentPlus();
            out.printIndent().println("this.fragNum = fragNum;");
            out.printIndent().println("this.xpContext = xpContext;");
            out.printIndent().println("this.xpTagParent = xpTagParent;");
            out.endBlock();
            out.println();
            out.printIndent().println("public org.anodyneos.xp.XpContext getXpContext() {");
            out.indentPlus();
            out.printIndent().println("return xpContext;");
            out.endBlock();
            out.println();
            out.printIndent().println("public void invoke() throws org.anodyneos.xp.XpException, javax.servlet.jsp.el.ELException, org.xml.sax.SAXException {");
            out.indentPlus();
            out.printIndent().println("switch(this.fragNum) {");
            out.indentPlus();
            for (int i = 0; i < getTranslaterContext().getFragmentCount(); i++) {
                out.printIndent().println("case " + i + ":");
                out.indentPlus();
                out.printIndent().println("invoke" + i + "();");
                out.printIndent().println("break;");
                out.indentMinus();
            }
            out.endBlock();
            out.endBlock();
            out.println();

            for (int i = 0; i < getTranslaterContext().getFragmentCount(); i++) {
                out.printIndent().println("public void invoke" + i + "() throws org.anodyneos.xp.XpException, javax.servlet.jsp.el.ELException, org.xml.sax.SAXException  {");
                out.indentPlus();
                out.printIndent().println("org.anodyneos.xp.XpContentHandler xpCH = xpContext.getXpContentHandler();");
                out.printIndent().println("javax.servlet.jsp.el.ExpressionEvaluator elEvaluator = xpContext.getExpressionEvaluator();");
                out.printIndent().println("javax.servlet.jsp.el.VariableResolver varResolver = xpContext.getVariableResolver();");
                out.println();
                out.print(getTranslaterContext().getFragment(i));
                out.endBlock();
                out.println();
            }
            out.println();
            out.endBlock();
            out.println();
        }

        // end of class definition
        out.println();
        out.endBlock();
    }
}
