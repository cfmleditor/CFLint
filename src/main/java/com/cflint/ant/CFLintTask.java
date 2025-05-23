package com.cflint.ant;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.ProgressMonitor;
import javax.xml.transform.TransformerException;

import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;

import com.cflint.CFLint;
import com.cflint.HTMLOutput;
import com.cflint.TextOutput;
import com.cflint.XMLOutput;
import com.cflint.config.CFLintConfig;
import com.cflint.config.CFLintConfiguration;
import com.cflint.config.CFLintPluginInfo.PluginInfoRule.PluginMessage;
import com.cflint.config.ConfigUtils;
import com.cflint.tools.CFLintFilter;
import com.cflint.xml.stax.DefaultCFlintResultMarshaller;

public class CFLintTask extends Task {

    private boolean showProgress;
    private String includeRule;
    private String excludeRule;
    private File filterFile;
    private File xmlFile;
    private File htmlFile;
    private File textFile;
    private File configFile;
    private String xmlStyle = "cflint";
    private String htmlStyle = "plain.xsl";
    private String extensions;
    private boolean verbose;
    private boolean quiet;
    private final List<FileSet> filesets = new ArrayList<>();

    @SuppressWarnings("deprecation")
    @Override
    public void execute() {
        FileInputStream fis = null;
        try {
            CFLintConfiguration config = null;
            if (configFile != null) {
                if (configFile.getName().toLowerCase().endsWith(".xml")) {
                    config = ConfigUtils.unmarshal(configFile, CFLintConfig.class);
                } else {
                    config = ConfigUtils.unmarshalJson(new FileInputStream(configFile), CFLintConfig.class);
                }
            }
            CFLintConfiguration cmdLineConfig = null;
            if ((excludeRule != null && excludeRule.trim().length() > 0)
                    || (includeRule != null && includeRule.trim().length() > 0)) {
                cmdLineConfig = new CFLintConfig();
                if (includeRule != null && includeRule.trim().length() > 0) {
                    for (final String code : includeRule.trim().split(",")) {
                        cmdLineConfig.addInclude(new PluginMessage(code));
                    }
                }
                if (excludeRule != null && excludeRule.trim().length() > 0) {
                    for (final String code : excludeRule.trim().split(",")) {
                        cmdLineConfig.addExclude(new PluginMessage(code));
                    }
                }
            }
            
            final CFLint cflint = new CFLint(config);
            cflint.setVerbose(verbose);
            cflint.setQuiet(quiet);
            if (extensions != null && extensions.trim().length() > 0) {
                cflint.setAllowedExtensions(Arrays.asList(extensions.trim().split(",")));
            }
            CFLintFilter filter = CFLintFilter.createFilter(verbose);
            if (filterFile != null) {
                final File ffile = filterFile;
                if (ffile.exists()) {
                    fis = new FileInputStream(ffile);
                    final byte[] b = new byte[fis.available()];
                    if (fis.read(b) > 0) {
                        fis.close();
                        filter = CFLintFilter.createFilter(new String(b), verbose);
                    }
                }
            }

            cflint.getBugs().setFilter(filter);
            if (xmlFile == null && htmlFile == null && textFile == null) {
                xmlFile = new File("cflint-result.xml");
            }
            if (xmlFile != null) {
                if (verbose) {
                    System.out.println("Style:" + xmlStyle);
                }
                if ("findbugs".equalsIgnoreCase(xmlStyle)) {
                    new XMLOutput().outputFindBugs(cflint.getBugs(), createWriter(xmlFile, StandardCharsets.UTF_8), cflint.getStats());
                } else {
                    new DefaultCFlintResultMarshaller().output(cflint.getBugs(), createWriter(xmlFile, StandardCharsets.UTF_8),cflint.getStats());
                }
            }
            if (textFile != null) {
                final Writer textwriter = textFile != null ? new FileWriter(textFile)
                        : new OutputStreamWriter(System.out);
                new TextOutput().output(cflint.getBugs(), textwriter, cflint.getStats());

            }
            if (htmlFile != null) {
                try {
                    new HTMLOutput(htmlStyle).output(cflint.getBugs(), new FileWriter(htmlFile), cflint.getStats());
                } catch (final TransformerException e) {
                    throw new IOException(e);
                }
            }
            for (final FileSet fileset : filesets) {
                int progress = 1;
                final DirectoryScanner ds = fileset.getDirectoryScanner(getProject()); // 3
                final ProgressMonitor progressMonitor = showProgress && !filesets.isEmpty()
                        ? new ProgressMonitor(null, "CFLint", "", 1, ds.getIncludedFilesCount()) : null;
                final String[] includedFiles = ds.getIncludedFiles();
                for (final String includedFile : includedFiles) {
                    if (progressMonitor != null) {
                        if (progressMonitor.isCanceled()) {
                            throw new RuntimeException("CFLint scan cancelled");
                        }
                        final String filename = ds.getBasedir() + File.separator + includedFile;
                        progressMonitor.setNote("scanning " + includedFile);
                        cflint.scan(filename);
                        progressMonitor.setProgress(progress++);
                    }
                }
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (final IOException e) {
            }
        }
    }

    
    /** 
     * @param xmlFile2 xmlFile2
     * @param encoding encoding
     * @return Writer
     * @throws IOException IOException
     */
    private Writer createWriter(final File xmlFile2, final Charset encoding) throws IOException {
        OutputStreamWriter out = null;
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(xmlFile2);
            out = new OutputStreamWriter(fos, encoding);
            out.append(String.format("<?xml version=\"1.0\" encoding=\"%s\" ?>%n", encoding));
        } catch (final Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (fos != null) {
                fos.close();
            }
        }
        return out;
    }

    public void addFileset(final FileSet fileset) {
        filesets.add(fileset);
    }

    public void setShowProgress(final boolean showProgress) {
        this.showProgress = showProgress;
    }

    public void setIncludeRule(final String includeRule) {
        this.includeRule = includeRule;
    }

    public void setExcludeRule(final String excludeRule) {
        this.excludeRule = excludeRule;
    }

    public void setFilterFile(final File filterFile) {
        this.filterFile = filterFile;
    }

    public void setXmlFile(final File xmlFile) {
        this.xmlFile = xmlFile;
    }

    public void setHtmlFile(final File htmlFile) {
        this.htmlFile = htmlFile;
    }

    public void setTextFile(final File textFile) {
        this.textFile = textFile;
    }

    public void setXmlStyle(final String xmlStyle) {
        this.xmlStyle = xmlStyle;
    }

    public void setHtmlStyle(final String htmlStyle) {
        this.htmlStyle = htmlStyle;
    }

    public void setExtensions(final String extensions) {
        this.extensions = extensions;
    }

    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

    public void setQuiet(final boolean quiet) {
        this.quiet = quiet;
    }

    public void setConfigFile(final File configFile) {
        this.configFile = configFile;
    }

}
