package com.cflint.plugins.core;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;

import com.cflint.BugList;
import com.cflint.CF;
import com.cflint.plugins.CFLintScannerAdapter;
import com.cflint.plugins.Context;
import cfml.parsing.cfscript.CFExpression;
import cfml.parsing.cfscript.CFFullVarExpression;
import cfml.parsing.cfscript.CFIdentifier;
import cfml.parsing.cfscript.CFMember;
import cfml.parsing.cfscript.CFVarDeclExpression;
import net.htmlparser.jericho.Element;

public class ImplicitScopeChecker extends CFLintScannerAdapter {

	/**
	 * Report each occurrence once per file/function
	 */
	// private Set<String> alreadyReportedExpression = new HashSet<>();
	// private Set<String> unqualifiedExpression = new HashSet<>();
	// private Set<String> alreadyReportedFullExpression = new HashSet<>();

    // LinkedHashMap is ordered.
    protected Set<String> scopedVariables = new HashSet<>();
    protected Set<String> variableScopedVariables = new HashSet<>();

    protected Map<String, VariableInfo> unscopedAssignedVariables = new LinkedHashMap<>();
    protected Map<String, VariableInfo> implicitIdentifierVariables = new LinkedHashMap<>();
    protected Boolean isCFM = false;

    // Scope keywords themselves (e.g. StructKeyExists(VARIABLES,"x"), StructClear(local))
    // are never scope-searched reads - they pass the scope struct itself as a value.
    private final Collection<String> scopes = Arrays.asList(CF.APPLICATION, CF.ATTRIBUTES, CF.REQUEST, CF.SERVER, CF.SESSION, CF.CFTHREAD, CF.FORM, CF.URL, CF.CGI, CF.COOKIE, CF.CLIENT, CF.FILE, CF.CFCATCH, CF.VARIABLES, CF.LOCAL, CF.ARGUMENTS, CF.THIS, CF.SUPER, CF.CALLER, CF.THISTAG);
    private final Collection<String> variablescopes = Arrays.asList(CF.VARIABLES);
    // CF's built-in query-loop properties are always available bare inside a query
    // loop/cfoutput, independent of the implicit-scope setting.
    private final Collection<String> queryLoopProperties = Arrays.asList("currentrow", "recordcount", "columnlist");

	
    /** 
     * @param expression expression
     * @param context context
     * @param bugs bugs
     */
    @Override
	public void expression(final CFExpression expression, final Context context, final BugList bugs) {
        if(expression instanceof CFFullVarExpression) {
            checkFullExpression((CFFullVarExpression) expression, context, bugs);
        } else if (expression instanceof CFVarDeclExpression) {
            checkExpression((CFVarDeclExpression)expression, context);
        } else if (expression instanceof CFIdentifier) {
            final String name = ((CFIdentifier) expression).getName();
            if ( name != null ) {
                final boolean checkIsScope = isScope(name) || isQueryLoopProperty(name);
                // Loop item/index bindings, query-loop columns, cfcatch/cfargument names etc
                // are pushed onto the call stack regardless of whether we're inside a function.
                final boolean checkIsCallStackVariable = context.getCallStack().checkVariable(name);

                if ( !checkIsScope && !checkIsCallStackVariable ) {
                    if (context.isInAssignmentExpression() && !(expression.getParent() instanceof CFMember) ) {
                        if ( !context.isInStructKeyExpression() ) {
                            unscopedAssignedVariables.put(name.toLowerCase(), new VariableInfo(name, expression, context));
                            //if ( context.isInAssignmentExpression() ) {
                                variableScopedVariables.add(name.toLowerCase());
                            //}
                        }
                    } else {
                        if ( !context.isInStructKeyExpression() ) {
                            implicitIdentifierVariables.put(name.toLowerCase(), new VariableInfo(name, expression, context));
                        }
                    }
                }
            }
        }
	}

	private void checkFullExpression(final CFFullVarExpression expression, final Context context, final BugList bugs) {
        final CFExpression variable = expression.getExpressions().get(0);
        final CFExpression variable2 = expression.getExpressions().size() > 1 ? expression.getExpressions().get(1) : null;
        if (variable instanceof CFIdentifier) {
            final CFIdentifier cfIdentifier1 = (CFIdentifier) variable;
			final CFIdentifier cfIdentifier2 = variable2 != null && variable2 instanceof CFIdentifier
							? (CFIdentifier) variable2
							    /* : ( variable2 instanceof CFMember 
                                    ? (((CFMember) variable2).getExpression() instanceof CFIdentifier 
                                        ? (CFIdentifier) ((CFMember) variable2).getExpression() 
                                        : null ) */
                                    : null;
            
            final String name1 = ((CFIdentifier) cfIdentifier1).getName();
            if ( isVariableScope(name1) && cfIdentifier2 != null  ) {
                final String name = ((CFIdentifier) cfIdentifier2).getName();
                if ( name != null ) {
                    variableScopedVariables.add(name.toLowerCase());
                }
            } else if ( isScope(name1) && cfIdentifier2 != null  ) {
                final String name = ((CFIdentifier) cfIdentifier2).getName();
                if ( name != null ) {
                    scopedVariables.add(name.toLowerCase());
                }
            } else if ( expression.getExpressions().size() == 1 ) {
                if ( name1 != null ) {
                    final boolean checkIsCallStackVariable = context.getCallStack().checkVariable(name1);
                    if ( !checkIsCallStackVariable ) {
                        unscopedAssignedVariables.put(name1.toLowerCase(), new VariableInfo(name1,cfIdentifier1,context));
                        if ( context.isInAssignmentExpression() ) {
                            variableScopedVariables.add(name1.toLowerCase());
                        }
                    }
                }
            }
        }

        for (CFExpression subexpr : expression.getExpressions()) {
            if (subexpr instanceof CFMember) {
                CFMember memberExpr = (CFMember) subexpr;
                if (memberExpr.getExpression() != null) {
                    expression(memberExpr.getExpression(), context, bugs);
                }
            }
        }

	}

	private void checkExpression(final CFVarDeclExpression expression, final Context context) {
        // final String name = expression.getName();
        // implicitIdentifierVariables.put(name.toLowerCase(), new VariableInfo(name, expression, context));
	}

    @Override
	public void startFile(String filename, BugList bugs) {
        final String ext = filename.substring(filename.length() - 3, filename.length());
        isCFM = "cfm".equals(ext);
        clearVariables(false);
	}

    @Override
	public void beforeEndFile(String filename, Context context, BugList bugs) {
        checkImplicitScopes(false, context);
	}

	@Override
	public void startFunction(Context context, BugList bugs) {
        clearVariables(true);
	}

    @Override
    public void endFunction(final Context context, final BugList bugs) {
        checkImplicitScopes(true, context);
    }
    
    @Override
    public void element(final Element element, final Context context, final BugList bugs) {
    }

    private void clearVariables(final Boolean isFunction) {
        if ( (isFunction && isCFM) || (!isFunction && !isCFM) ) {
            return;
        }
        implicitIdentifierVariables.clear();
        unscopedAssignedVariables.clear();
        scopedVariables.clear();
    }
    
    private void checkImplicitScopes(final Boolean isFunction, final Context context) {
        if ( (isFunction && isCFM) || (!isFunction && !isCFM) ) {
            return;
        }
        // sort by line number
        for (final VariableInfo variable : implicitIdentifierVariables.values()) {
            final String lname = variable.name.toLowerCase();
            // Flag only when nothing ever establishes this name in variables/local scope
            // in this file/function. Bare writes always land in variables/local, so once
            // one exists, later bare reads never touch the implicit-scope fallback chain -
            // regardless of whether the same name is also read from url/form/etc elsewhere.
            final boolean hasVariablesScopeWrite = unscopedAssignedVariables.get(lname) != null
                    || variableScopedVariables.contains(lname);
            if ( !hasVariablesScopeWrite ) {
                context.addMessage(
                    "IMPLICIT_SCOPE",
                    variable.name,
                    this,
                    variable.lineNumber,
                    variable.offset,
                    variable.expression,
                    variable.context);
            }
        }
    }

    private boolean isScope(final String nameVar) {
        return nameVar != null && scopes.contains(nameVar.toLowerCase().trim());
    }

    private boolean isVariableScope(final String nameVar) {
        return nameVar != null && variablescopes.contains(nameVar.toLowerCase().trim());
    }

    private boolean isQueryLoopProperty(final String nameVar) {
        return nameVar != null && queryLoopProperties.contains(nameVar.toLowerCase().trim());
    }

    public static class VariableInfo {
        private Integer lineNumber;
        private Integer offset;
        private String name;
        CFExpression expression;
        final Context context;

        public VariableInfo(final String name, final CFExpression expression,final Context context) {
            super();
            this.name = name;
            this.expression = expression;
            this.context=context;
        }
    }
}
