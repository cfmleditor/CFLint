package com.cflint;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Before;
import org.junit.Test;

import com.cflint.api.CFLintAPI;
import com.cflint.api.CFLintResult;
import com.cflint.config.ConfigBuilder;
import com.cflint.exception.CFLintScanException;

public class TestCFBugs_ImplicitScope {

    private CFLintAPI cfBugs;

    
    /** 
     * @throws Exception Exception
     */
    @Before
    public void setUp() throws Exception {
        final ConfigBuilder configBuilder = new ConfigBuilder().include("IMPLICIT_SCOPE");
        cfBugs = new CFLintAPI(configBuilder.build());
    }

    @Test
    public void testImplicitScope() throws CFLintScanException {
        final String cfcSrc = "<cfcomponent>\r\n" 
                + "<cffunction name=\"test\">\r\n"
                + "	<cfset URL.test1=\"xyz\">\r\n"
                + "<cfif test1></cfif>\r\n"
                + "<cfoutput>#test3#</cfoutput>\r\n"
                + "<cfif URL.test2></cfif>\r\n" 
                + "<cfset var test5 = \"\">\r\n" 
                + "<cfif test5></cfif>\r\n" 
                + "<cfset test5 = {test6 = \"test7\"} />\r\n"
                + "<cfset test9[test8] = \"test\" />\r\n" 
                + "<cfset URL.test1 = test5[test10] />\r\n" 
                // + "<cfset VARIABLES.test5 = \"\">\r\n" 
                // + "<cfif URL.test6></cfif>\r\n" 
                // + "<cfif test6></cfif>\r\n" 
                // + "<cfset VARIABLES.test6 = \"\">\r\n" 
                + "</cffunction>\r\n"
                + "</cfcomponent>";
        CFLintResult lintresult = cfBugs.scan(cfcSrc, "test.cfc");
        final List<BugInfo> result = lintresult.getIssues().values().iterator().next();
        assertEquals(4, result.size());
        assertEquals("IMPLICIT_SCOPE", result.get(0).getMessageCode());
        assertEquals("IMPLICIT_SCOPE", result.get(1).getMessageCode());
        assertEquals("IMPLICIT_SCOPE", result.get(2).getMessageCode());
        assertEquals("IMPLICIT_SCOPE", result.get(3).getMessageCode());
        assertEquals(4, result.get(0).getLine());
        assertEquals(5, result.get(1).getLine());
        assertEquals(10, result.get(2).getLine());
        assertEquals(11, result.get(3).getLine());
    }

    @Test
    public void testImplicitScopeCFM() throws CFLintScanException {
        final String cfmSrc = "<cfset URL.temp = \"\" />\r\n<cfif temp></cfif>";
        CFLintResult lintresult = cfBugs.scan(cfmSrc, "test.cfm");
        final List<BugInfo> result = lintresult.getIssues().values().iterator().next();
        assertEquals(1, result.size());
        assertEquals("IMPLICIT_SCOPE", result.get(0).getMessageCode());
        assertEquals(2, result.get(0).getLine());
    }

    // A prior bare assignment sourced from an implicit scope (mode = URL.mode) still lands
    // in variables scope - later bare reads of "mode" never touch the fallback chain.
    @Test
    public void testPriorAssignmentFromImplicitScopeNotFlagged() throws CFLintScanException {
        final String cfmSrc = "<cfset mode = URL.mode />\r\n<cfif mode EQ 1></cfif>";
        CFLintResult lintresult = cfBugs.scan(cfmSrc, "test.cfm");
        assertEquals(0, lintresult.getIssues().getOrDefault("IMPLICIT_SCOPE", java.util.Collections.emptyList()).size());
    }

    // A <cfquery name="..."> attribute defines the variable - it's a write, not a read.
    @Test
    public void testCfqueryNameAttributeNotFlagged() throws CFLintScanException {
        final String cfmSrc = "<cfquery name=\"foo\">SELECT 1</cfquery>\r\n<cfif foo.recordCount GT 0></cfif>";
        CFLintResult lintresult = cfBugs.scan(cfmSrc, "test.cfm");
        assertEquals(0, lintresult.getIssues().getOrDefault("IMPLICIT_SCOPE", java.util.Collections.emptyList()).size());
    }

    // cfloop's item= attribute binds a variable for the loop body.
    @Test
    public void testCfloopItemNotFlagged() throws CFLintScanException {
        final String cfmSrc = "<cfloop collection=\"#StructNew()#\" item=\"bar\">\r\n<cfoutput>#bar#</cfoutput>\r\n</cfloop>";
        CFLintResult lintresult = cfBugs.scan(cfmSrc, "test.cfm");
        assertEquals(0, lintresult.getIssues().getOrDefault("IMPLICIT_SCOPE", java.util.Collections.emptyList()).size());
    }

    // Scope keywords passed as a literal argument to a scope-introspection built-in are
    // not a scope-searched read of an unscoped variable.
    @Test
    public void testScopeKeywordAsLiteralArgumentNotFlagged() throws CFLintScanException {
        final String cfmSrc = "<cfif StructKeyExists(VARIABLES,\"username\")></cfif>";
        CFLintResult lintresult = cfBugs.scan(cfmSrc, "test.cfm");
        assertEquals(0, lintresult.getIssues().getOrDefault("IMPLICIT_SCOPE", java.util.Collections.emptyList()).size());
    }

    // <cfoutput query="q"> binds q's columns for the duration of the tag, same as <cfloop query="q">.
    @Test
    public void testCfoutputQueryColumnNotFlagged() throws CFLintScanException {
        final String cfmSrc = "<cfquery name=\"q\">SELECT col1, col2 FROM t</cfquery>\r\n<cfoutput query=\"q\">#col1#</cfoutput>";
        CFLintResult lintresult = cfBugs.scan(cfmSrc, "test.cfm");
        assertEquals(0, lintresult.getIssues().getOrDefault("IMPLICIT_SCOPE", java.util.Collections.emptyList()).size());
    }

}
