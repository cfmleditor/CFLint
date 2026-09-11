package com.cflint.plugins.core;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.cflint.BugList;
import com.cflint.CF;
import com.cflint.plugins.CFLintScannerAdapter;
import com.cflint.plugins.Context;

import net.htmlparser.jericho.Element;

public class CFXTagChecker extends CFLintScannerAdapter {

    // tagName is a regex supplied by the configuration (and can be overridden per folder via
    // .cflintrc) - cache the compiled Pattern per distinct config value, rather than
    // recompiling it for every tag in the scan.
    private final Map<String, Pattern> tagNamePatternCache = new HashMap<>();

    
    /** 
     * @param element element
     * @param context context
     * @param bugs bugs
     */
    @Override
    public void element(final Element element, final Context context, final BugList bugs) {
        final String tagName = element.getName();
        final String cfmlTagCheck = context.getConfiguration().getParameter(this,"tagName");
        final String scope = context.getConfiguration().getParameter(this,"scope");

        if (cfmlTagCheck != null
                && tagNamePatternCache.computeIfAbsent(cfmlTagCheck, Pattern::compile).matcher(tagName).matches()) {
            if (scope == null || scope.equals(CF.COMPONENT) && context.isInComponent()) {
                context.addMessage("AVOID_USING_" + tagName.toUpperCase() + "_TAG", tagName);
            }
        }
    }
}
