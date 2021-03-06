/**
 * Copyright (C) FuseSource, Inc.
 * http://fusesource.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fusesource.fabric.zookeeper.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.apache.zookeeper.CreateMode;
import org.fusesource.fabric.zookeeper.IZKClient;

import static org.fusesource.fabric.zookeeper.utils.RegexSupport.getPatterns;
import static org.fusesource.fabric.zookeeper.utils.RegexSupport.matches;

public class ZookeeperImportUtils {


    private ZookeeperImportUtils() {
        //Utility Class
    }

    public static void importFromFileSystem(IZKClient zooKeeper, String source, String target, String includeRegex[], String excludeRegex[], boolean delete, boolean dryRun, boolean verbose) throws Exception {
        Map<String, String> settings = new TreeMap<String, String>();
        File s = new File(source);
        getCandidates(zooKeeper, s, s, settings, target);
        List<Pattern> include = getPatterns(includeRegex);
        List<Pattern> exclude = getPatterns(excludeRegex);

        if (!target.endsWith("/")) {
            target = target + "/";
        }
        if (!target.startsWith("/")) {
            target = "/" + target;
        }

        List<String> paths = new ArrayList<String>();

        for (String key : settings.keySet()) {
            String data = settings.get(key);
            key = target + key;
            paths.add(key);
            if (!matches(include, key, true) || matches(exclude, key, false)) {
                continue;
            }
            if (!dryRun) {
                if (data != null) {
                    if (verbose) {
                        System.out.println("importing: " + key);
                    }
                    zooKeeper.createOrSetWithParents(key, data, CreateMode.PERSISTENT);
                }
            } else {
                System.out.printf("Creating path \"%s\" with value \"%s\"\n", key, data);
            }
        }

        if (delete) {
            deletePathsNotIn(zooKeeper, paths, target, dryRun);
        }
    }

    public static void importFromPropertiesFile(IZKClient zooKeeper, String source, String target, String includeRegex[], String excludeRegex[], boolean dryRun) throws Exception {
        List<Pattern> includes = getPatterns(includeRegex);
        List<Pattern> excludes = getPatterns(excludeRegex);
        InputStream in = new BufferedInputStream(new URL(source).openStream());
        List<String> paths = new ArrayList<String>();
        Properties props = new Properties();
        props.load(in);
        for (Enumeration names = props.propertyNames(); names.hasMoreElements(); ) {
            String name = (String) names.nextElement();
            String value = props.getProperty(name);
            if (value != null && value.isEmpty()) {
                value = null;
            }
            if (!name.startsWith("/")) {
                name = "/" + name;
            }
            name = target + name;
            if (!matches(includes, name, true) || matches(excludes, name, false)) {
                continue;
            }
            if (!dryRun) {
                zooKeeper.createOrSetWithParents(name, value, CreateMode.PERSISTENT);
            } else {
                System.out.printf("Creating path \"%s\" with value \"%s\"\n", name, value);
            }
        }
    }


    private static void getCandidates(IZKClient zookeeper, File parent, File current, Map<String, String> settings, String target) throws Exception {
        List<Pattern> profile = getPatterns(new String[]{RegexSupport.PROFILE_REGEX});
        List<Pattern> containerProperties = getPatterns(new String[]{RegexSupport.PROFILE_CONTAINER_PROPERTIES_REGEX});
        List<Pattern> attributes = getPatterns(new String[]{RegexSupport.PROFILE_ATTRIBUTES_REGEX});
        if (current.isDirectory()) {
            for (File child : current.listFiles()) {
                getCandidates(zookeeper, parent, child, settings, target);
            }
            String p = buildZKPath(parent, current).replaceFirst("/", "");
            if (!matches(profile, "/" + p, false)) {
                settings.put(p, null);
            }
        } else {
            BufferedInputStream in = new BufferedInputStream(new FileInputStream(current));
            byte[] contents = new byte[in.available()];
            in.read(contents);
            in.close();
            String p = buildZKPath(parent, current).replaceFirst("/", "");
            if (p.endsWith(".cfg")) {
                p = p.substring(0, p.length() - ".cfg".length());
            }

            if (matches(attributes, "/" + p, false)) {
                settings.put(p.substring(0, p.lastIndexOf("/")), new String(contents));
            } else if (matches(containerProperties, "/" + p, false)) {
                settings.put(p, new String(contents).replaceAll(RegexSupport.PARENTS_REGEX, ""));
                Properties props = new Properties();
                props.load(new StringReader(new String(contents)));
                if (settings.get(p.substring(0, p.lastIndexOf("/"))) == null) {
                    String parents = (String) props.get("parents");
                    if (parents != null && !parents.isEmpty()) {
                        settings.put(p.substring(0, p.lastIndexOf("/")), "parents=" + parents);
                    }
                }
            } else if (!matches(profile, "/" + p, false)) {
                settings.put(p, new String(contents));
            }
        }
    }


    private static String buildZKPath(File parent, File current) {
        String rc = "";
        if (current != null && !parent.equals(current)) {
            rc = buildZKPath(parent, current.getParentFile()) + "/" + current.getName();
        }
        return rc;
    }

    private static void deletePathsNotIn(IZKClient zookeeper, List<String> paths, String target, boolean dryRun) throws Exception {
        List<String> zkPaths = zookeeper.getAllChildren(target);

        for (String path : zkPaths) {
            path = "/" + path;
            if (!paths.contains(path)) {
                if (!dryRun) {
                    zookeeper.deleteWithChildren(path);
                } else {
                    System.out.printf("Deleting path %s and everything under it\n", path);
                }
            }
        }
    }
}
