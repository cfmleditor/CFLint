package com.cflint;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import com.cflint.api.CFLintAPI;
import com.cflint.api.CFLintResult;
import com.cflint.config.ConfigBuilder;
import com.cflint.exception.CFLintScanException;

public class TestCFInsertTagChecker {

    private CFLintAPI cfBugs;

    
    /** 
     * @throws Exception Exception
     */
    @Before
    public void setUp() throws Exception {
        final ConfigBuilder configBuilder = new ConfigBuilder().include("AVOID_USING_CFINSERT_TAG");
        cfBugs = new CFLintAPI(configBuilder.build());
    }

    @Test
    public void test_BAD() throws CFLintScanException {
        final String cfcSrc = "<cfinsert " + "dataSource = \"data source name\" " + "tableName = \"table name\" "
                + "formFields = \"formfield1, formfield2, ...\" " + "password = \"password\" "
                + "tableOwner = \"owner\" " + "tableQualifier = \"table qualifier\" " + "username = \"user name\">";
        CFLintResult lintresult = cfBugs.scan(cfcSrc, "test");
        assertEquals(1, lintresult.getIssues().size());
    }

    @Test
    public void test_GOOD() throws CFLintScanException {
        final String cfcSrc = "<cfmodule template=\"tagsExchRateCalculator.cfm\">";
        CFLintResult lintresult = cfBugs.scan(cfcSrc, "test");
        assertEquals(0, lintresult.getIssues().size());
    }

}
