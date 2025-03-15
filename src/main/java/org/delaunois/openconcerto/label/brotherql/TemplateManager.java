/*
 * Openconcerto Module for printing labels with BrotherQL printers.
 *
 * Copyright (C) 2024 Cédric de Launois
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.delaunois.openconcerto.label.brotherql;

import org.delaunois.openconcerto.label.brotherql.graphicspl.GraphicsPL;
import org.openconcerto.utils.FileUtils;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads and manage graphicspl label templates
 *
 * @author Cedric de Launois
 */
@SuppressWarnings({"unused", "SameParameterValue"})
public class TemplateManager {

    private static final Logger LOGGER = Logger.getLogger(TemplateManager.class.getName());

    private static final String TEMPLATES_RESOURCES_DIR = "Templates/Labels/";
    private static final String[] TEMPLATE_PATH = new String[]{
            "Templates/Labels",
            "Configuration/Template/Labels"
    };

    /**
     * Get the list of graphicspl template names available.
     *
     * @return the template list
     */
    public List<String> getNames() {
        List<String> names = new ArrayList<>();
        browseTemplates(f -> {
            names.add(getName(f));
            return true;
        });
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /**
     * Read the graphicspl template content from the given template name.
     *
     * @param templateName the template name
     * @return the template content
     */
    public String get(String templateName) {
        File[] template = new File[]{null};
        browseTemplates(f -> {
            if (templateName.equals(getName(f))) {
                template[0] = f;
                return false;
            }
            return true;
        });

        return get(template[0]);
    }

    public List<Template> getAll() {
        List<Template> templates = new ArrayList<>();
        browseTemplates(f -> {
            GraphicsPL g = new GraphicsPL();
            try {
                g.load(f);
                final Element root = g.getDocument().getDocumentElement();
                final int width = Integer.parseInt(root.getAttribute("width"));
                final int height = Integer.parseInt(root.getAttribute("height"));
                templates.add(new Template(getName(f), get(f), g, width, height));
            } catch (ParserConfigurationException | SAXException | IOException e) {
                // Ignore
            }
            return true;
        });
        return templates;
    }
    /**
     * Read the graphicspl template content from the given file.
     *
     * @param f the template file
     * @return the template content or null if f is null
     */
    public String get(File f) {
        if (f != null) {
            try {
                return FileUtils.read(f, StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to read template", e);
            }
        }
        return null;
    }

    /**
     * Get the name of the graphicspl template file.
     *
     * @param f the graphicspl file, expected to end with .graphicspl extension
     * @return the template content
     */
    public String getName(File f) {
        return f.getName().substring(0, f.getName().length() - ".graphicspl".length()).trim();
    }

    /**
     * Apply a function to each graphicspl template
     *
     * @param processor a function receiving the template file and returning true to continue the browse or false
     *                  to stop it
     */
    public void browseTemplates(Function<File, Boolean> processor) {
        for (String path : TEMPLATE_PATH) {
            File dir = new File(path);
            if (!browseTemplates(dir, processor)) {
                return;
            }
        }
    }

    public static void installTemplate() throws IOException, URISyntaxException {
        File templateDir = null;
        for (String path : TEMPLATE_PATH) {
            File dir = new File(path);
            if (dir.exists() && dir.isDirectory()) {
                templateDir = dir;
            }
        }

        if (templateDir == null) {
            LOGGER.log(Level.WARNING, "Failed to find template directory");
            return;
        }

        // Get templates from jar
        String[] templates = getResourceListing(TemplateManager.class, TEMPLATES_RESOURCES_DIR);
        
        // Copy non-existing templates to template dir
        for (String templateName : templates) {
            InputStream is = TemplateManager.class.getResourceAsStream("/" + TEMPLATES_RESOURCES_DIR + templateName);
            if (is == null) {
                continue;
            }
            String srcTemplate = FileUtils.read(is, StandardCharsets.UTF_8);
            File dstFile = new File(templateDir, templateName);
            if (dstFile.exists()) {
                LOGGER.log(Level.INFO, "Skipping template {0} because it already exists", templateName);
            } else {
                LOGGER.log(Level.INFO, "Installing template {0}", templateName);
                if (dstFile.createNewFile()) {
                    FileWriter myWriter = new FileWriter(dstFile);
                    myWriter.write(srcTemplate);
                    myWriter.close();
                }
            }
        }
    }

    // from https://www.uofr.net/~greg/java/get-resource-listing.html

    /**
     * List directory contents for a resource folder. Not recursive.
     * This is basically a brute-force implementation.
     * Works for regular files and also JARs.
     *
     * @param clazz Any java class that lives in the same place as the resources you want.
     * @param path  Should end with "/", but not start with one.
     * @return Just the name of each member item, not the full paths.
     * @throws URISyntaxException in case of error
     * @throws IOException        in case of error
     * @author Greg Briggs
     */
    private static String[] getResourceListing(Class<?> clazz, String path) throws URISyntaxException, IOException {
        URL dirURL = clazz.getClassLoader().getResource(path);
        if (dirURL != null && dirURL.getProtocol().equals("file")) {
            /* A file path: easy enough */
            return new File(dirURL.toURI()).list();
        }

        if (dirURL == null) {
            /*
             * In case of a jar file, we can't actually find a directory.
             * Have to assume the same jar as clazz.
             */
            String me = clazz.getName().replace(".", "/") + ".class";
            dirURL = clazz.getClassLoader().getResource(me);
        }

        if (dirURL == null || !dirURL.getProtocol().equals("jar")) {
            throw new UnsupportedOperationException("Cannot list files for URL " + dirURL);
        }

        /* A JAR path */
        String jarPath = dirURL.getPath().substring(5, dirURL.getPath().indexOf("!")); //strip out only the JAR file
        Enumeration<JarEntry> entries;
        Set<String> result = new HashSet<>(); //avoid duplicates in case it is a subdirectory
        try (JarFile jar = new JarFile(URLDecoder.decode(jarPath, StandardCharsets.UTF_8))) {
            //gives ALL entries in jar
            entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith(path)) { //filter according to the path
                    String entry = name.substring(path.length());
                    int checkSubdir = entry.indexOf("/");
                    if (checkSubdir >= 0) {
                        // if it is a subdirectory, we just return the directory name
                        entry = entry.substring(0, checkSubdir);
                    }
                    result.add(entry);
                }
            }
        }
        return result.toArray(new String[0]);
    }

    private boolean browseTemplates(File templatesDir, Function<File, Boolean> processor) {
        if (!templatesDir.exists() || !templatesDir.isDirectory()) {
            return true;
        }

        File[] files = templatesDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().endsWith(".graphicspl")) {
                    if (!processor.apply(f)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

}
