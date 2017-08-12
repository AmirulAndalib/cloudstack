//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

package com.cloud.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

public class PropertiesUtil {
    private static final Logger s_logger = Logger.getLogger(PropertiesUtil.class);

    public static String getDefaultApiCommandsFileName() {
        return "commands.properties";
    }

    /**
     * Searches the class path and local paths to find the config file.
     * @param path path to find.  if it starts with / then it's absolute path.
     * @return File or null if not found at all.
     */

    public static File findConfigFile(String path) {
        ClassLoader cl = PropertiesUtil.class.getClassLoader();
        URL url = cl.getResource(path);
        if (url != null && "file".equals(url.getProtocol())) {
            return new File(url.getFile());
        }

        url = ClassLoader.getSystemResource(path);
        if (url != null && "file".equals(url.getProtocol())) {
            return new File(url.getFile());
        }

        File file = new File(path);
        if (file.exists()) {
            return file;
        }

        String newPath = "conf" + (path.startsWith(File.separator) ? "" : "/") + path;
        url = ClassLoader.getSystemResource(newPath);
        if (url != null && "file".equals(url.getProtocol())) {
            return new File(url.getFile());
        }

        url = cl.getResource(newPath);
        if (url != null && "file".equals(url.getProtocol())) {
            return new File(url.getFile());
        }

        newPath = "conf" + (path.startsWith(File.separator) ? "" : File.separator) + path;
        file = new File(newPath);
        if (file.exists()) {
            return file;
        }

        newPath = System.getProperty("catalina.home");
        if (newPath == null) {
            newPath = System.getenv("CATALINA_HOME");
        }

        if (newPath == null) {
            newPath = System.getenv("CATALINA_BASE");
        }

        if (newPath == null) {
            return null;
        }

        file = new File(newPath + File.separator + "conf" + File.separator + path);
        if (file.exists()) {
            return file;
        }

        return null;
    }

    public static Map<String, Object> toMap(Properties props) {
        Set<String> names = props.stringPropertyNames();
        HashMap<String, Object> map = new HashMap<String, Object>(names.size());
        for (String name : names) {
            map.put(name, props.getProperty(name));
        }

        return map;
    }

    /*
     * Returns an InputStream for the given resource
     * This is needed to read the files within a jar in classpath.
     */
    public static InputStream openStreamFromURL(String path) {
        ClassLoader cl = PropertiesUtil.class.getClassLoader();
        URL url = cl.getResource(path);
        if (url != null) {
            try {
                InputStream stream = url.openStream();
                return stream;
            } catch (IOException ioex) {
                return null;
            }
        }
        return null;
    }

    // Returns key=value pairs by parsing a commands.properties/config file
    // with syntax; key=cmd;value (with this syntax cmd is stripped) and key=value
    public static Map<String, String> processConfigFile(String[] configFiles) {
        Map<String, String> configMap = new HashMap<String, String>();
        Properties preProcessedCommands = new Properties();
        for (String configFile : configFiles) {
            File commandsFile = findConfigFile(configFile);
            if (commandsFile != null) {
                try {
                    loadFromFile(preProcessedCommands, commandsFile);
                } catch (FileNotFoundException fnfex) {
                    // in case of a file within a jar in classpath, try to open stream using url
                    InputStream stream = PropertiesUtil.openStreamFromURL(configFile);
                    if (stream != null) {
                        try {
                            preProcessedCommands.load(stream);
                        } catch (IOException e) {
                            s_logger.error("IO Exception, unable to find properties file:", fnfex);
                        }
                    } else {
                        s_logger.error("Unable to find properites file", fnfex);
                    }
                } catch (IOException ioe) {
                    s_logger.error("IO Exception loading properties file", ioe);
                }
            }
        }

        for (Object key : preProcessedCommands.keySet()) {
            String preProcessedCommand = preProcessedCommands.getProperty((String)key);
            int splitIndex = preProcessedCommand.lastIndexOf(";");
            String value = preProcessedCommand.substring(splitIndex + 1);
            configMap.put((String)key, value);
        }
        return configMap;
    }

    /**
     * Load a Properties object with contents from a File.
     * @param properties the properties object to be loaded
     * @param file  the file to load from
     * @throws IOException
     */
    public static void loadFromFile(Properties properties, File file) throws IOException {
        InputStream stream = new FileInputStream(file);
        try {
            properties.load(stream);
        } finally {
            IOUtils.closeQuietly(stream);
        }
    }

    public static Properties getProperties(final File file) {
        final Properties properties = new Properties();
        if (file == null || !file.exists()) {
            return properties;
        }
        try {
            PropertiesUtil.loadFromFile(properties, file);
        } catch (final IOException ex) {
            s_logger.error("Unable to load properties from file: " + file.getAbsolutePath() + ", due to :", ex);
        }
        return properties;
    }
}
