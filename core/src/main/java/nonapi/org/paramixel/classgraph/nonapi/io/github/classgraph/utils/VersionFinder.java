/*
 * Copyright (c) 2026-present Douglas Hoard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nonapi.org.paramixel.classgraph.nonapi.io.github.classgraph.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;
import nonapi.org.paramixel.classgraph.io.github.classgraph.ClassGraph;
import org.w3c.dom.Document;

/** Finds the version number of ClassGraph, and the version of the JDK. */
public final class VersionFinder {

    /** The Maven package for ClassGraph. */
    private static final String MAVEN_PACKAGE = "nonapi.org.paramixel.classgraph.io.github.classgraph";

    /** The Maven artifact for ClassGraph. */
    private static final String MAVEN_ARTIFACT = "classgraph";

    /** The operating system type. */
    public static final OperatingSystem OS;

    /** Java version string. */
    public static final String JAVA_VERSION = getProperty("java.version");

    /** Java major version -- 7 for "1.7", 8 for "1.8.0_244", 9 for "9", 11 for "11-ea", etc. */
    public static final int JAVA_MAJOR_VERSION;

    /** Java minor version -- 0 for "11.0.4" */
    public static final int JAVA_MINOR_VERSION;

    /** Java minor version -- 4 for "11.0.4" */
    public static final int JAVA_SUB_VERSION;

    /** Java is EA release -- true for "11-ea", etc. */
    public static final boolean JAVA_IS_EA_VERSION;

    static {
        int javaMajorVersion = 0;
        int javaMinorVersion = 0;
        int javaSubVersion = 0;
        final List<Integer> versionParts = new ArrayList<>();
        if (JAVA_VERSION != null) {
            for (final String versionPart : JAVA_VERSION.split("[^0-9]+")) {
                try {
                    versionParts.add(Integer.parseInt(versionPart));
                } catch (final NumberFormatException e) {
                    // Skip
                }
            }
            if (!versionParts.isEmpty() && versionParts.get(0) == 1) {
                // 1.7 or 1.8 -> 7 or 8
                versionParts.remove(0);
            }
            if (versionParts.isEmpty()) {
                throw new RuntimeException("Could not determine Java version: " + JAVA_VERSION);
            }
            javaMajorVersion = versionParts.get(0);
            if (versionParts.size() > 1) {
                javaMinorVersion = versionParts.get(1);
            }
            if (versionParts.size() > 2) {
                javaSubVersion = versionParts.get(2);
            }
        }
        JAVA_MAJOR_VERSION = javaMajorVersion;
        JAVA_MINOR_VERSION = javaMinorVersion;
        JAVA_SUB_VERSION = javaSubVersion;
        JAVA_IS_EA_VERSION = JAVA_VERSION != null && JAVA_VERSION.endsWith("-ea");
    }

    /** The operating system type. */
    public enum OperatingSystem {
        /** Windows. */
        Windows,

        /** Mac OS X. */
        MacOSX,

        /** Linux. */
        Linux,

        /** Solaris. */
        Solaris,

        /** BSD. */
        BSD,

        /** Unix or AIX. */
        Unix,

        /** Unknown. */
        Unknown
    }

    static {
        final String osName = getProperty("os.name", "unknown").toLowerCase(Locale.ENGLISH);
        if (File.separatorChar == '\\') {
            OS = OperatingSystem.Windows;
        } else if (osName == null) {
            OS = OperatingSystem.Unknown;
        } else if (osName.contains("win")) {
            OS = OperatingSystem.Windows;
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            OS = OperatingSystem.MacOSX;
        } else if (osName.contains("nux")) {
            OS = OperatingSystem.Linux;
        } else if (osName.contains("sunos") || osName.contains("solaris")) {
            OS = OperatingSystem.Solaris;
        } else if (osName.contains("bsd")) {
            OS = OperatingSystem.Unix;
        } else if (osName.contains("nix") || osName.contains("aix")) {
            OS = OperatingSystem.Unix;
        } else {
            OS = OperatingSystem.Unknown;
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Constructor.
     */
    private VersionFinder() {
        // Cannot be constructed
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Get a system property (returning null if a SecurityException was thrown).
     *
     * @param propName
     *            the property name
     * @return the property value
     */
    public static String getProperty(final String propName) {
        try {
            return System.getProperty(propName);
        } catch (final SecurityException e) {
            return null;
        }
    }

    /**
     * Get a system property (returning null if a SecurityException was thrown).
     *
     * @param propName
     *            the property name
     * @param defaultVal
     *            the default value for the property
     * @return the property value, or the default if the property is not defined.
     */
    public static String getProperty(final String propName, final String defaultVal) {
        try {
            return System.getProperty(propName, defaultVal);
        } catch (final SecurityException e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Get the version number of ClassGraph.
     *
     * @return the version number of ClassGraph.
     */
    public static synchronized String getVersion() {
        // Try to get version number from pom.xml (available when running in Eclipse)
        final Class<?> cls = ClassGraph.class;
        try {
            final String className = cls.getName();
            final URL classpathResource = cls.getResource("/" + JarUtils.classNameToClassfilePath(className));
            if (classpathResource != null) {
                final Path absolutePackagePath =
                        Paths.get(classpathResource.toURI()).getParent();
                final int packagePathSegments =
                        className.length() - className.replace(".", "").length();
                // Remove package segments from path
                Path path = absolutePackagePath;
                for (int i = 0; i < packagePathSegments && path != null; i++) {
                    path = path.getParent();
                }
                // Remove up to two more levels for "bin" or "target/classes"
                for (int i = 0; i < 3 && path != null; i++, path = path.getParent()) {
                    final Path pom = path.resolve("pom.xml");
                    try (InputStream is = Files.newInputStream(pom)) {
                        final Document doc = getSecureDocumentBuilderFactory()
                                .newDocumentBuilder()
                                .parse(is);
                        doc.getDocumentElement().normalize();
                        String version = (String) getSecureXPathFactory()
                                .newXPath()
                                .compile("/project/version")
                                .evaluate(doc, XPathConstants.STRING);
                        if (version != null) {
                            version = version.trim();
                            if (!version.isEmpty()) {
                                return version;
                            }
                        }
                    } catch (final IOException e) {
                        // Not found
                    }
                }
            }
        } catch (final Exception e) {
            // Ignore
        }

        // Try to get version number from maven properties in jar's META-INF directory
        try (InputStream is = cls.getResourceAsStream(
                "/META-INF/maven/" + MAVEN_PACKAGE + "/" + MAVEN_ARTIFACT + "/pom.properties")) {
            if (is != null) {
                final Properties p = new Properties();
                p.load(is);
                final String version = p.getProperty("version", "").trim();
                if (!version.isEmpty()) {
                    return version;
                }
            }
        } catch (final IOException e) {
            // Ignore
        }

        // Fallback to using Java API (version number is obtained from MANIFEST.MF)
        final Package pkg = cls.getPackage();
        if (pkg != null) {
            String version = pkg.getImplementationVersion();
            if (version == null) {
                version = "";
            }
            version = version.trim();
            if (version.isEmpty()) {
                version = pkg.getSpecificationVersion();
                if (version == null) {
                    version = "";
                }
                version = version.trim();
            }
            if (!version.isEmpty()) {
                return version;
            }
        }
        return "unknown";
    }

    /**
     * Helper method to provide a XXE secured DocumentBuilder Factory.
     *
     * reference - https://gist.github.com/AlainODea/1779a7c6a26a5c135280bc9b3b71868f
     *
     * reference - https://rules.sonarsource.com/java/tag/owasp/RSPEC-2755
     *
     * @return DocumentBuilderFactory
     * @throws ParserConfigurationException
     */
    private static DocumentBuilderFactory getSecureDocumentBuilderFactory() throws ParserConfigurationException {
        final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setXIncludeAware(false);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        dbf.setExpandEntityReferences(false);
        dbf.setNamespaceAware(true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return dbf;
    }

    /**
     * Helper method to provide a XXE secured XPathFactory Factory.
     *
     * reference - https://rules.sonarsource.com/java/tag/owasp/RSPEC-2755
     *
     * @return XPathFactory
     * @throws XPathFactoryConfigurationException
     */
    private static XPathFactory getSecureXPathFactory() throws XPathFactoryConfigurationException {
        final XPathFactory xPathFactory = XPathFactory.newInstance();
        xPathFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return xPathFactory;
    }
}
