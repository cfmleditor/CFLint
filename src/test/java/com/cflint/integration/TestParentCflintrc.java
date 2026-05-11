package com.cflint.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import com.cflint.api.CFLintAPI;
import com.cflint.api.CFLintResult;

public class TestParentCflintrc {

    @Test
    public void testParentCflintrcApplied() throws Exception {
        // Scan a file in child/ directory - the .cflintrc in parentconfig/ (parent dir)
        // should restrict rules to only MISSING_VAR
        CFLintAPI api = new CFLintAPI();
        CFLintResult result = api.scan(
                Arrays.asList("src/test/resources/com/cflint/parentconfig/child/test.cfc"));

        String json = result.getJSON();

        // MISSING_VAR should fire because the parent .cflintrc includes it
        assertTrue("Expected MISSING_VAR in output", json.contains("MISSING_VAR"));

        // FUNCTION_HINT_MISSING should NOT fire because the parent .cflintrc
        // uses inheritParent=false and only includes MISSING_VAR
        assertTrue("FUNCTION_HINT_MISSING should be excluded by parent .cflintrc",
                !json.contains("FUNCTION_HINT_MISSING"));
    }

    @Test
    public void testWithoutCflintrcAllRulesFire() throws Exception {
        // Scan the same source content but from a temp location without .cflintrc
        // to confirm FUNCTION_HINT_MISSING would normally fire
        CFLintAPI api = new CFLintAPI();
        String source = "component {\n  public function test() {\n    someVar = \"hello\";\n    return someVar;\n  }\n}";
        CFLintResult result = api.scan(source, "test.cfc");

        String json = result.getJSON();

        // Both rules should fire without .cflintrc restriction
        assertTrue("Expected MISSING_VAR", json.contains("MISSING_VAR"));
        assertTrue("Expected FUNCTION_HINT_MISSING", json.contains("FUNCTION_HINT_MISSING"));
    }
}
