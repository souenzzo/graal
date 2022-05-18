/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.hub;

import static com.oracle.svm.core.configure.ConfigurationParser.asList;

import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import com.oracle.svm.core.configure.ConfigurationParser;
import com.oracle.svm.core.util.json.JSONParserException;

public final class FilterAdvisor {

    public static final Pattern PROXY_CLASS_NAME_PATTERN = Pattern.compile("^(.+[/.])?\\$Proxy[0-9]+$");

    private static final HierarchyFilterNode internalCallerFilter;

    private static final HierarchyFilterNode internalAccessFilter;

    private static final HierarchyFilterNode accessWithoutCallerFilter;

    static {
        internalCallerFilter = HierarchyFilterNode.createInclusiveRoot();

        internalCallerFilter.addOrGetChildren("com.sun.crypto.provider.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("com.sun.java.util.jar.pack.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("com.sun.net.ssl.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("com.sun.nio.file.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("com.sun.nio.sctp.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("com.sun.nio.zipfs.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.io.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.lang.**", ConfigurationFilter.Inclusion.Exclude);
        // The agent should not filter calls from native libraries (JDK11).
        internalCallerFilter.addOrGetChildren("java.lang.ClassLoader$NativeLibrary", ConfigurationFilter.Inclusion.Include);
        internalCallerFilter.addOrGetChildren("java.math.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.net.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.nio.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.text.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.time.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("java.util.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("javax.crypto.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("javax.lang.model.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("javax.net.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("javax.tools.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("jdk.internal.**", ConfigurationFilter.Inclusion.Exclude);
        // The agent should not filter calls from native libraries (JDK17).
        internalCallerFilter.addOrGetChildren("jdk.internal.loader.NativeLibraries$NativeLibraryImpl", ConfigurationFilter.Inclusion.Include);
        internalCallerFilter.addOrGetChildren("jdk.jfr.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("jdk.net.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("jdk.nio.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("jdk.vm.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.invoke.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.launcher.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.misc.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.net.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.nio.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.reflect.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.text.**", ConfigurationFilter.Inclusion.Exclude);
        internalCallerFilter.addOrGetChildren("sun.util.**", ConfigurationFilter.Inclusion.Exclude);

        excludeInaccessiblePackages(internalCallerFilter);

        internalCallerFilter.removeRedundantNodes();

        internalAccessFilter = HierarchyFilterNode.createInclusiveRoot();
        excludeInaccessiblePackages(internalAccessFilter);
        internalAccessFilter.removeRedundantNodes();

        accessWithoutCallerFilter = HierarchyFilterNode.createInclusiveRoot(); // in addition to
                                                                               // accessFilter

        accessWithoutCallerFilter.addOrGetChildren("jdk.vm.ci.**", ConfigurationFilter.Inclusion.Exclude);
        accessWithoutCallerFilter.addOrGetChildren("[Ljava.lang.String;", ConfigurationFilter.Inclusion.Exclude);
        // ^ String[]: for command-line argument arrays created before Java main method is called
        accessWithoutCallerFilter.removeRedundantNodes();
    }

    /*
     * Exclude selection of packages distributed with GraalVM which are not unconditionally exported
     * by their module and should not be accessible from application code. Generate all with:
     * native-image-configure generate-filters --exclude-unexported-packages-from-modules [--reduce]
     */
    private static void excludeInaccessiblePackages(HierarchyFilterNode rootNode) {
        rootNode.addOrGetChildren("com.oracle.graal.**", ConfigurationFilter.Inclusion.Exclude);
        rootNode.addOrGetChildren("com.oracle.truffle.**", ConfigurationFilter.Inclusion.Exclude);
        rootNode.addOrGetChildren("org.graalvm.compiler.**", ConfigurationFilter.Inclusion.Exclude);
        rootNode.addOrGetChildren("org.graalvm.libgraal.**", ConfigurationFilter.Inclusion.Exclude);
    }

    public static boolean shouldIgnore(String qualifiedCaller) {
        assert qualifiedCaller == null || qualifiedCaller.indexOf('/') == -1 : "expecting Java-format qualifiers, not internal format";
        if (qualifiedCaller != null && !internalCallerFilter.includes(qualifiedCaller)) {
            return true;
        }
        return false;
    }

    public static boolean shouldIgnore(String queriedClass, String qualifiedCaller) {
        assert qualifiedCaller == null || qualifiedCaller.indexOf('/') == -1 : "expecting Java-format qualifiers, not internal format";
        if (qualifiedCaller != null && !internalCallerFilter.includes(qualifiedCaller)) {
            return true;
        }
        // NOTE: queriedClass can be null for non-class accesses like resources
        if (qualifiedCaller == null && queriedClass != null && !accessWithoutCallerFilter.includes(queriedClass)) {
            return true;
        }
        if (internalAccessFilter != null && queriedClass != null && !internalAccessFilter.includes(queriedClass)) {
            return true;
        }
        return queriedClass != null && PROXY_CLASS_NAME_PATTERN.matcher(queriedClass).matches();
    }
}

class JsonWriter implements AutoCloseable {
    private final Writer writer;

    private int indentation = 0;

    public JsonWriter(Path path, OpenOption... options) throws IOException {
        this(Files.newBufferedWriter(path, StandardCharsets.UTF_8, options));
    }

    public JsonWriter(Writer writer) {
        this.writer = writer;
    }

    public JsonWriter append(char c) throws IOException {
        writer.write(c);
        return this;
    }

    public JsonWriter append(String s) throws IOException {
        writer.write(s);
        return this;
    }

    public JsonWriter quote(Object o) throws IOException {
        if (o == null) {
            return append("null");
        } else if (Boolean.TRUE.equals(o)) {
            return append("true");
        } else if (Boolean.FALSE.equals(o)) {
            return append("false");
        }
        return quote(o.toString());
    }

    public JsonWriter quote(String s) throws IOException {
        writer.write(quoteString(s));
        return this;
    }

    public static String quoteString(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(2 + s.length() + 8 /* room for escaping */);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\');
                sb.append(c);
            } else if (c < 0x001F) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    public JsonWriter newline() throws IOException {
        StringBuilder builder = new StringBuilder(1 + 2 * indentation);
        builder.append("\n");
        for (int i = 0; i < indentation; ++i) {
            builder.append("  ");
        }
        writer.write(builder.toString());
        return this;
    }

    public JsonWriter indent() {
        indentation++;
        return this;
    }

    public JsonWriter unindent() {
        assert indentation > 0;
        indentation--;
        return this;
    }

    public void flush() throws IOException {
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}

interface JsonPrintable {
    void printJson(JsonWriter writer) throws IOException;
}

interface ConfigurationFilter extends JsonPrintable {

    void parseFromJson(Map<String, Object> topJsonObject);

    boolean includes(String qualifiedName);

    /** Inclusion status of a filter. */
    enum Inclusion {
        Include("+"),
        Exclude("-");

        final String s;

        Inclusion(String s) {
            this.s = s;
        }

        @Override
        public String toString() {
            return s;
        }

        public Inclusion invert() {
            return (this == Include) ? Exclude : Include;
        }
    }
}

final class HierarchyFilterNode implements ConfigurationFilter {

    /** Everything that is not included is considered excluded. */
    private static final Inclusion DEFAULT_INCLUSION = Inclusion.Exclude;

    private static final String CHILDREN_PATTERN = "*";
    private static final String DESCENDANTS_PATTERN = "**";

    /** The non-qualified name. The qualified name is derived from the names of all parents. */
    private final String name;

    /** Inclusion for the exact qualified name when queried via {@link #includes}. */
    private Inclusion inclusion;

    /** Inclusion for immediate children except those in {@link #children}. */
    private Inclusion childrenInclusion;

    /** Inclusion for descendants not covered by {@link #children} or {@link #childrenInclusion}. */
    private Inclusion descendantsInclusion;

    /** Explicit rules for all immediate children. */
    private Map<String, HierarchyFilterNode> children;

    public static HierarchyFilterNode createRoot() {
        return new HierarchyFilterNode("");
    }

    public static HierarchyFilterNode createInclusiveRoot() {
        HierarchyFilterNode root = new HierarchyFilterNode("");
        root.addOrGetChildren("**", ConfigurationFilter.Inclusion.Include);
        return root;
    }

    private HierarchyFilterNode(String unqualifiedName) {
        this.name = unqualifiedName;
    }

    public void addOrGetChildren(String qualifiedName, Inclusion leafInclusion) {
        if (leafInclusion != Inclusion.Include && leafInclusion != Inclusion.Exclude) {
            throw new IllegalArgumentException(leafInclusion + " not supported");
        }
        HierarchyFilterNode parent = this;
        StringTokenizer tokenizer = new StringTokenizer(qualifiedName, ".", false);
        /*
         * We split the qualified name, such as "com.sun.crypto.**", into its individual tokens.
         * Only the last token and possibly the second-to-last token, "**" and "crypto" in this
         * example, are the tokens for which the include or exclude action is relevant. Intermediate
         * tokens, such as "com" and "sun", are irrelevant unless rules for them are added in a
         * separate call. Otherwise, we leave them undecided for inclusion/exclusion.
         */
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            boolean isLeaf = !tokenizer.hasMoreTokens();
            boolean isPattern = (token.indexOf('*') != -1);
            if (isPattern) {
                boolean recursive = token.equals(DESCENDANTS_PATTERN);
                if (!isLeaf || (!recursive && !token.equals(CHILDREN_PATTERN))) {
                    throw new IllegalArgumentException(qualifiedName + ": only " + CHILDREN_PATTERN + " and " + DESCENDANTS_PATTERN +
                                    " are allowed as the entire pattern (no complex patterns), and only in the last position");
                }
                if (recursive) { // "**" replaces "*" and all descendant rules
                    parent.descendantsInclusion = leafInclusion;
                    parent.childrenInclusion = null;
                    parent.children = null;
                } else {
                    parent.childrenInclusion = leafInclusion;
                    if (parent.children != null) { // "*" replaces all immediate child rules
                        parent.children.values().removeIf(c -> {
                            c.inclusion = null;
                            return c.isRedundantLeaf();
                        });
                    }
                }
            } else {
                HierarchyFilterNode node = null;
                if (parent.children != null) {
                    node = parent.children.get(token);
                } else {
                    parent.children = new HashMap<>();
                }
                if (node != null) {
                    if (isLeaf) {
                        node.inclusion = leafInclusion;
                    }
                } else {
                    node = new HierarchyFilterNode(token);
                    node.inclusion = isLeaf ? leafInclusion : null;
                    parent.children.put(token, node);
                }
                parent = node;
            }
        }
    }

    private boolean isLeafNode() {
        return children == null || children.isEmpty();
    }

    /**
     * Transforms a tree that must not contain recursive rules so that it becomes a smaller tree
     * with recursive rules. The resulting tree is not as precise as the original tree however, and
     * may match expressions that were not explicitly included in the original tree, but it never
     * matches expressions that were explicitly excluded in the original tree.
     */
    public void reduceExhaustiveTree() {
        if (children != null) {
            for (HierarchyFilterNode child : children.values()) {
                child.reduceExhaustiveTree0();
            }
        }
        removeRedundantNodes();
    }

    private int reduceExhaustiveTree0() {
        if (descendantsInclusion != null) {
            throw new IllegalStateException("Recursive rules are not allowed");
        }
        if (children != null) {
            int sum = 0;
            for (HierarchyFilterNode child : children.values()) {
                sum += child.reduceExhaustiveTree0();
            }
            if (descendantsInclusion == null) {
                descendantsInclusion = (sum > 0) ? Inclusion.Include : Inclusion.Exclude;
            }
        }
        int score = 0;
        score = addToScore(inclusion, score);
        score = addToScore(childrenInclusion, score);
        score = addToScore(descendantsInclusion, score);
        return score;
    }

    private static int addToScore(Inclusion inclusion, int score) {
        if (inclusion == Inclusion.Include) {
            return score + 1;
        } else if (inclusion == Inclusion.Exclude) {
            return score - 1;
        }
        return score;
    }

    /** Removes redundant nodes that are covered by a recursive ancestor. */
    public void removeRedundantNodes() {
        removeRedundantDescendants(DEFAULT_INCLUSION);
    }

    private void removeRedundantDescendants(Inclusion inheritedDescendantsInclusion) {
        if (!isLeafNode()) {
            Inclusion descendantsDefaultInclusion = (descendantsInclusion != null) ? descendantsInclusion : inheritedDescendantsInclusion;
            Inclusion childrenDefaultInclusion = (childrenInclusion != null) ? childrenInclusion : descendantsDefaultInclusion;
            children.values().removeIf(c -> c.removeRedundantNodes(childrenDefaultInclusion, descendantsDefaultInclusion));
        }
    }

    private boolean removeRedundantNodes(Inclusion defaultInclusion, Inclusion descendantsDefaultInclusion) {
        assert defaultInclusion == Inclusion.Include || descendantsDefaultInclusion == Inclusion.Exclude;
        assert descendantsDefaultInclusion == Inclusion.Include || descendantsDefaultInclusion == Inclusion.Exclude;
        removeRedundantDescendants(descendantsDefaultInclusion);
        if (inclusion == defaultInclusion) {
            inclusion = null;
        }
        if (descendantsInclusion == descendantsDefaultInclusion) {
            descendantsInclusion = null;
        }
        if (childrenInclusion == descendantsInclusion || (descendantsInclusion == null && childrenInclusion == descendantsDefaultInclusion)) {
            childrenInclusion = null;
        }
        return isRedundantLeaf();
    }

    private boolean isRedundantLeaf() {
        return inclusion == null && childrenInclusion == null && descendantsInclusion == null && isLeafNode();
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.quote("rules").append(": [").indent().newline();
        final boolean[] isFirstRule = {true};
        printJsonEntries(writer, isFirstRule, "");
        writer.unindent().newline();
        writer.append(']');
    }

    @Override
    public void parseFromJson(Map<String, Object> top) {
        Object rulesObject = top.get("rules");
        if (rulesObject != null) {
            List<Object> rulesList = asList(rulesObject, "Attribute 'list' must be a list of rule objects");
            for (Object entryObject : rulesList) {
                FilterConfigurationParser.parseEntry(entryObject, this::addOrGetChildren);
            }
        }
    }

    private void printJsonEntries(JsonWriter writer, boolean[] isFirstRule, String parentQualified) throws IOException {
        String qualified = parentQualified.isEmpty() ? name : (parentQualified + '.' + name);
        // NOTE: the order in which these rules are printed is important!
        String patternBegin = qualified.isEmpty() ? qualified : (qualified + ".");
        FilterConfigurationParser.printEntry(writer, isFirstRule, descendantsInclusion, patternBegin + DESCENDANTS_PATTERN);
        FilterConfigurationParser.printEntry(writer, isFirstRule, childrenInclusion, patternBegin + CHILDREN_PATTERN);
        FilterConfigurationParser.printEntry(writer, isFirstRule, inclusion, qualified);
        if (children != null) {
            HierarchyFilterNode[] sorted = children.values().toArray(new HierarchyFilterNode[0]);
            Arrays.sort(sorted, Comparator.comparing(child -> child.name));
            for (HierarchyFilterNode child : sorted) {
                child.printJsonEntries(writer, isFirstRule, qualified);
            }
        }
    }

    @Override
    public boolean includes(String qualifiedName) {
        HierarchyFilterNode current = this;
        Inclusion inheritedInclusion = DEFAULT_INCLUSION;
        StringTokenizer tokenizer = new StringTokenizer(qualifiedName, ".", false);
        while (tokenizer.hasMoreTokens()) {
            if (current.descendantsInclusion != null) {
                inheritedInclusion = current.descendantsInclusion;
            }
            String part = tokenizer.nextToken();
            HierarchyFilterNode child = (current.children != null) ? current.children.get(part) : null;
            if (child == null) {
                boolean isDirectChild = !tokenizer.hasMoreTokens();
                if (isDirectChild && current.childrenInclusion != null) {
                    return (current.childrenInclusion == Inclusion.Include);
                }
                return (inheritedInclusion == Inclusion.Include);
            }
            current = child;
        }
        return (current.inclusion == Inclusion.Include);
    }

    public HierarchyFilterNode copy() {
        HierarchyFilterNode copy = new HierarchyFilterNode(name);
        copy.inclusion = inclusion;
        copy.childrenInclusion = childrenInclusion;
        copy.descendantsInclusion = descendantsInclusion;
        if (children != null) {
            copy.children = new HashMap<>();
            for (Map.Entry<String, HierarchyFilterNode> entry : children.entrySet()) {
                HierarchyFilterNode childCopy = entry.getValue().copy();
                copy.children.put(entry.getKey(), childCopy);
            }
        }
        return copy;
    }
}

class FilterConfigurationParser extends ConfigurationParser {
    private final ConfigurationFilter filter;

    public FilterConfigurationParser(ConfigurationFilter filter) {
        super(true);
        assert filter != null;
        this.filter = filter;
    }

    static void parseEntry(Object entryObject, BiConsumer<String, ConfigurationFilter.Inclusion> parsedEntryConsumer) {
        Map<String, Object> entry = asMap(entryObject, "Filter entries must be objects");
        Object qualified = null;
        HierarchyFilterNode.Inclusion inclusion = null;
        String exactlyOneMessage = "Exactly one of attributes 'includeClasses' and 'excludeClasses' must be specified for a filter entry";
        for (Map.Entry<String, Object> pair : entry.entrySet()) {
            if (qualified != null) {
                throw new JSONParserException(exactlyOneMessage);
            }
            qualified = pair.getValue();
            if ("includeClasses".equals(pair.getKey())) {
                inclusion = ConfigurationFilter.Inclusion.Include;
            } else if ("excludeClasses".equals(pair.getKey())) {
                inclusion = ConfigurationFilter.Inclusion.Exclude;
            } else {
                throw new JSONParserException("Unknown attribute '" + pair.getKey() + "' (supported attributes: 'includeClasses', 'excludeClasses') in filter");
            }
        }
        if (qualified == null) {
            throw new JSONParserException(exactlyOneMessage);
        }
        parsedEntryConsumer.accept(asString(qualified), inclusion);
    }

    static void printEntry(JsonWriter writer, boolean[] isFirstRule, ConfigurationFilter.Inclusion inclusion, String rule) throws IOException {
        if (inclusion == null) {
            return;
        }
        if (!isFirstRule[0]) {
            writer.append(',').newline();
        } else {
            isFirstRule[0] = false;
        }
        writer.append('{');
        switch (inclusion) {
            case Include:
                writer.quote("includeClasses");
                break;
            case Exclude:
                writer.quote("excludeClasses");
                break;
            default:
                throw new IllegalStateException("Unsupported inclusion value: " + inclusion.name());
        }
        writer.append(':');
        writer.quote(rule);
        writer.append("}");
    }

    @Override
    public void parseAndRegister(Object json, URI origin) {
        filter.parseFromJson(asMap(json, "First level of document must be an object"));
    }

}
