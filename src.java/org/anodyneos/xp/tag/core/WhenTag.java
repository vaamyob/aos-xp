/*
 * Created on May 9, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package org.anodyneos.xp.tag.core;

import javax.servlet.jsp.el.ELException;

import org.anodyneos.xp.XpException;
import org.anodyneos.xp.XpOutput;
import org.anodyneos.xp.tagext.XpTagSupport;
import org.xml.sax.SAXException;


/**
 * @author jvas
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class WhenTag extends XpTagSupport {

    private boolean test = false;

    public void setTest(boolean test) {
        this.test = test;
    }

    public void doTag(XpOutput out) throws XpException, ELException, SAXException {
        ChooseTag parent = (ChooseTag) getParent();
        if (! parent.isComplete() && test) {
            getXpBody().invoke(out);
            parent.markComplete();
        }
    }

}
