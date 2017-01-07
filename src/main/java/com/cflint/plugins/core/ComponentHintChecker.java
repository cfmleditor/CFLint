package com.cflint.plugins.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cflint.BugList;
import com.cflint.plugins.CFLintScannerAdapter;
import com.cflint.plugins.Context;
import com.cflint.tools.CFTool;
import com.cflint.tools.PrecedingCommentReader;

import cfml.parsing.cfscript.CFExpression;
import cfml.parsing.cfscript.script.CFCompDeclStatement;
import cfml.parsing.cfscript.script.CFScriptStatement;
import net.htmlparser.jericho.Element;

public class ComponentHintChecker extends CFLintScannerAdapter {
	final String severity = "INFO";
	
	@Override
	public void element(final Element element, final Context context, final BugList bugs) {
		if (element.getName().equals("cfcomponent")) {
			final String hint = element.getAttributeValue("hint");
			if (hint == null || hint.trim().isEmpty()) {
				context.addMessage("COMPONENT_HINT_MISSING", context.calcComponentName());
			}
		}
	}

	@Override
	public void expression(CFScriptStatement expression, Context context, BugList bugs) {
		if(expression instanceof CFCompDeclStatement){
			final CFCompDeclStatement compDeclStatement = (CFCompDeclStatement) expression;
			final CFExpression hintAttribute = CFTool.convertMap(compDeclStatement.getAttributes()).get("hint");
			if(hintAttribute == null){
				final String _mlText = PrecedingCommentReader.getMultiLine(context, expression.getToken());
				final String mlText = _mlText==null?null:_mlText.replaceFirst("^/\\*", "").replaceAll("\\*/$", "").trim();
				if(mlText != null && !mlText.isEmpty()){
					final Pattern pattern = Pattern.compile(".*\\s*@hint\\s+([\\w,_]+)\\s*.*", Pattern.DOTALL);
					final Matcher matcher = pattern.matcher(mlText);
					if (matcher.matches()) {
						String hintText = matcher.group(1);
						if(hintText.trim().isEmpty()){
							context.addMessage("COMPONENT_HINT_MISSING", context.calcComponentName());		
						}
					}
				}else{
					context.addMessage("COMPONENT_HINT_MISSING", context.calcComponentName());
				}
			}
		}
	}

}
