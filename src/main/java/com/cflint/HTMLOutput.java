package com.cflint;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

/**
 * Output bug list in HTML format.
 */
public class HTMLOutput {
    /**
     * HTML style to output.
     */
    private final String htmlStyle;

    public HTMLOutput(final String htmlStyle) {
        super();
        this.htmlStyle = htmlStyle;
    }

    /**
     * Output bug list in HTML format.
     * @param bugList bugList
     * @param writer writer
     * @param stats stats
     * @throws IOException IOException
     * @throws TransformerException TransformerException
     */
    public void output(final BugList bugList, final Writer writer, final CFLintStats stats)
        throws IOException, TransformerException {

        InputStream is = getClass().getResourceAsStream("/findbugs/" + htmlStyle);
        if (is == null) {
            is = getClass().getResourceAsStream("/" + htmlStyle);
        }
        if (is == null) {
            throw new IOException("XSL resource not found: " + htmlStyle);
        }

        final TransformerFactory tFactory = new net.sf.saxon.TransformerFactoryImpl();
        final Transformer transformer = tFactory.newTransformer(
                new StreamSource(new InputStreamReader(is)));

        final StringWriter sw = new StringWriter();
        new XMLOutput().outputFindBugs(bugList, sw, stats);

        transformer.transform(new StreamSource(new StringReader(sw.toString())),
            new StreamResult(writer));

        writer.close();
    }
}
