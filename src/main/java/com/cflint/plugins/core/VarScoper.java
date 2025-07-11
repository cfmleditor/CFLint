package com.cflint.plugins.core;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cflint.BugList;
import com.cflint.CF;
import com.cflint.plugins.CFLintScannerAdapter;
import com.cflint.plugins.Context;
import com.cflint.plugins.Context.ContextType;

import cfml.parsing.cfscript.CFExpression;
import cfml.parsing.cfscript.CFFullVarExpression;
import cfml.parsing.cfscript.CFIdentifier;
import cfml.parsing.cfscript.CFLiteral;
import cfml.parsing.cfscript.CFStringExpression;
import cfml.parsing.cfscript.script.CFPropertyStatement;
import cfml.parsing.cfscript.script.CFScriptStatement;
import net.htmlparser.jericho.Element;

public class VarScoper extends CFLintScannerAdapter {

    public static final String VARIABLE = "variable";
    public static final String RESULT = "result";
    public static final String STRUCTNAME = "structname";

    private final Map<String, List<String>> checkElementAttributes = new HashMap<>();
    private final List<String> checkNames = Arrays.asList(CF.CFQUERY, CF.CFFEED, CF.CFDIRECTORY,
        CF.CFFORM, CF.CFFTP, CF.CFOBJECT, CF.CFSEARCH, CF.CFPROCRESULT, CF.CFPOP, CF.CFREGISTRY, CF.CFREPORT,
        CF.CFDBINFO, CF.CFDOCUMENT, CF.CFCOLLECTION, CF.CFPDF, CF.CFZIP, CF.CFLDAP, CF.CFHTTP, CF.CFCHART,
        CF.CFHTMLTOPDF, CF.CFIMAGE, CF.CFIMAP, CF.CFSHAREPOINT, CF.CFSPREADSHEET);
    private final Collection<String> scopes = Arrays.asList(CF.APPLICATION, CF.CGI, CF.COOKIE, CF.FORM, CF.REQUEST,
        CF.SERVER, CF.SESSION, CF.URL, CF.CFTHREAD);

    
    /** 
     * @param expression expression
     * @param context context
     * @param bugs bugs
     */
    @Override
    public void expression(final CFExpression expression, final Context context, final BugList bugs) {
        if (expression instanceof CFIdentifier) {
            // No issue
            if (expression instanceof CFFullVarExpression
                    && ((CFFullVarExpression) expression).getExpressions().size() > 1) {
                // Visit the first in the expression.
                expression(((CFFullVarExpression) expression).getExpressions().get(0), context, bugs);
                return;
            }
            final String name = ((CFIdentifier) expression).getName();
            if (context.isInFunction() && context.isInAssignmentExpression()
                && !context.getCallStack().checkVariable(name) && !isGlobal(name)
                ) {               
                boolean isInLocalMode = isInLocalMode(context);
                if(isInLocalMode) {
                    // If in local mode, we don't want to report missing variables
                    return;
                }

                context.addMessage("MISSING_VAR", name, context.startLine(), context.offset() + expression.getOffset());
            } else if (expression instanceof CFFullVarExpression) {
                final CFFullVarExpression fullVarExpr = (CFFullVarExpression) expression;
                expression(fullVarExpr.getExpressions().get(0), context, bugs);
            }
        }
    }

    private boolean isInLocalMode(Context curentContext){
         Context functionContext =  curentContext.getParent(ContextType.FUNCTION);
         if(functionContext == null){
                return false;
         }
        Map<CFExpression, CFExpression> attributes = functionContext.getFunctionAttributes();
        if(attributes == null || attributes.isEmpty()){
            return false;
        }
        for( Map.Entry<CFExpression, CFExpression> attr : attributes.entrySet()) {
            if(! (attr.getKey() instanceof CFIdentifier)){
                continue;
            }
            CFIdentifier identifier = (CFIdentifier) attr.getKey();
            if(!"localmode".equalsIgnoreCase(identifier.getName())){
                continue;
            }
            String valueString = getValueFromCFExpression(attr.getValue());

            if(valueString == null) {
                return false;
            }
            
            return "modern".equalsIgnoreCase(valueString) || "true".equalsIgnoreCase(valueString) || "yes".equalsIgnoreCase(valueString);
        }
        return false;
    }


    private String getValueFromCFExpression(final CFExpression expression) {
        if (expression instanceof CFLiteral) {
            return ((CFLiteral) expression).getImage();
        } else if (expression instanceof CFStringExpression) {
            final CFStringExpression stringExpression = (CFStringExpression) expression;
            if (stringExpression.getSubExpressions().size() == 1 && stringExpression.getSubExpressions().get(0) instanceof CFLiteral) {
                return getValueFromCFExpression(stringExpression.getSubExpressions().get(0));
            }
        }
        return null;
    }

    @Override
    public void expression(final CFScriptStatement expression, final Context context, final BugList bugs) {
        if (expression instanceof CFPropertyStatement) {
            //final CFPropertyStatement propertyStatement = (CFPropertyStatement)expression;
        }
    }

    public VarScoper() {
        checkElementAttributes.put(CF.CFEXECUTE, Arrays.asList(VARIABLE));
        checkElementAttributes.put(CF.CFFEED, Arrays.asList(CF.QUERY));
        checkElementAttributes.put(CF.CFFILE, Arrays.asList(VARIABLE, RESULT));
        checkElementAttributes.put(CF.CFFTP, Arrays.asList(RESULT));
        checkElementAttributes.put(CF.CFHTTP, Arrays.asList(RESULT));
        checkElementAttributes.put(CF.CFIMAGE, Arrays.asList(STRUCTNAME));
        checkElementAttributes.put(CF.CFINVOKE, Arrays.asList(CF.RETURNVARIABLE));
        checkElementAttributes.put(CF.CFLOOP, Arrays.asList(CF.INDEX, CF.ITEM));
        checkElementAttributes.put(CF.CFNTAUTHENTICATE, Arrays.asList(RESULT));
        checkElementAttributes.put(CF.CFPROCPARAM, Arrays.asList(VARIABLE));
        checkElementAttributes.put(CF.CFQUERY, Arrays.asList(RESULT));
        checkElementAttributes.put(CF.CFREGISTRY, Arrays.asList(VARIABLE));
        checkElementAttributes.put(CF.CFSAVECONTENT, Arrays.asList(VARIABLE));
        checkElementAttributes.put(CF.CFSPREADSHEET, Arrays.asList(CF.QUERY));
        checkElementAttributes.put(CF.CFSTOREDPROC, Arrays.asList(RESULT));
        checkElementAttributes.put(CF.CFWDDX, Arrays.asList(CF.OUTPUT));
        checkElementAttributes.put(CF.CFXML, Arrays.asList(VARIABLE));
        checkElementAttributes.put(CF.CFZIP, Arrays.asList(VARIABLE));
    }

    @Override
    public void element(final Element element, final Context context, final BugList bugs) {
        final String name = element.getName();
        final int line = element.getSource().getRow(element.getBegin());
        int offset = element.getBegin();
        if (name != null && name.trim().length() > 0 && context.isInFunction()) {
            if (checkNames.contains(name.toLowerCase())) {
                offset = element.getAttributes().get(CF.NAME) != null ? element.getAttributes().get(CF.NAME).getValueSegment().getBegin() : offset;
                assertVariable(element, context, bugs, element.getAttributeValue(CF.NAME), line, offset);
            }
            if (checkElementAttributes.containsKey(name.toLowerCase())) {
                for (final String attrName : checkElementAttributes.get(name.toLowerCase())) {
                    offset = element.getAttributes().get(attrName) != null
                            ? element.getAttributes().get(attrName).getValueSegment().getBegin()
                            : offset;
                    assertVariable(element, context, bugs, element.getAttributeValue(attrName), line, offset);
                }
            }
        }
    }

    protected void assertVariable(final Element element, final Context context, final BugList bugs,
                                  final String inameVar, int line, int offset) {
        final String nameVar = inameVar == null ? null : inameVar.split("\\.")[0].split("\\[")[0];
        if (nameVar != null && !context.getCallStack().checkVariable(nameVar) && !isGlobal(nameVar)) {
            context.addMessage("MISSING_VAR", inameVar, line, offset);
        }
    }

    private boolean isGlobal(final String nameVar) {
        return nameVar != null && scopes.contains(nameVar.toLowerCase().trim());
    }

}
