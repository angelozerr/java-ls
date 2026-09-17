/**
 * Copyright (c) 2026 IBM Corporation and others.
 *
 * Licensed under the MIT License.
 *
 * SPDX-License-Identifier: MIT
 *
 * Author: Angelo Zerr
 */
package ch.castleridge.javals.settings;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import ch.castleridge.javals.JsonUtils;
import ch.castleridge.javals.analysis.Declaration;

/**
 * POJO representation of the {@code javals} settings sent by the LSP
 * client, both in {@code initializationOptions} and in
 * {@code workspace/didChangeConfiguration}. Deserialised via
 * {@link JsonUtils#toModel(Object, Class)}.
 */
public class JavaLSSettings {

    /** All declaration kinds enabled by default. */
    public static final Set<Declaration.Kind> ALL_CODELENS_KINDS =
            Set.copyOf(EnumSet.allOf(Declaration.Kind.class));

    private String workspacePath;
    private Integer referencesCandidateCap;
    private Boolean indexClassFileContents;
    private Boolean prunedSourceIndexing;
    private CodeLensSettings codeLens;
    private BackendSettings backend;

    // Wrapper field: settings may arrive wrapped in { "javals": { ... } }
    private JavaLSSettings javals;

    /**
     * Converts a raw LSP settings object into a {@link JavaLSSettings},
     * automatically unwrapping a possible {@code "javals"} wrapper.
     */
    public static JavaLSSettings fromSettings(Object settings) {
        if (settings == null) {
            return new JavaLSSettings();
        }
        JavaLSSettings result = JsonUtils.toModel(settings, JavaLSSettings.class);
        if (result == null) {
            return new JavaLSSettings();
        }
        return result.javals != null ? result.javals : result;
    }

    public String getWorkspacePath() {
        return workspacePath;
    }

    public Integer getReferencesCandidateCap() {
        return referencesCandidateCap;
    }

    public Boolean getIndexClassFileContents() {
        return indexClassFileContents;
    }

    public Boolean getPrunedSourceIndexing() {
        return prunedSourceIndexing;
    }

    public CodeLensSettings getCodeLens() {
        return codeLens;
    }

    public BackendSettings getBackend() {
        return backend;
    }

    /**
     * Returns the set of declaration kinds for the "references" CodeLens.
     * Defaults to {@link #ALL_CODELENS_KINDS} when absent.
     */
    public Set<Declaration.Kind> getCodeLensShowReferences() {
        return codeLens != null ? codeLens.getShowReferencesKinds() : ALL_CODELENS_KINDS;
    }

    /**
     * Returns a normalised {@link BackendSettings}, never {@code null}.
     */
    public BackendSettings getBackendOrDefault() {
        return backend != null ? backend : new BackendSettings();
    }

    /**
     * CodeLens-related settings.
     */
    public static class CodeLensSettings {
        private List<String> showReferences;

        public List<String> getShowReferences() {
            return showReferences;
        }

        /**
         * Converts the {@code showReferences} string list into a typed
         * {@link Declaration.Kind} set. Returns {@link #ALL_CODELENS_KINDS}
         * when absent.
         */
        public Set<Declaration.Kind> getShowReferencesKinds() {
            if (showReferences == null) {
                return ALL_CODELENS_KINDS;
            }
            EnumSet<Declaration.Kind> kinds = EnumSet.noneOf(Declaration.Kind.class);
            for (String s : showReferences) {
                kindFromString(s).ifPresent(kinds::add);
            }
            return Set.copyOf(kinds);
        }
    }

    /**
     * Compiler/indexer backend selection.
     */
    public static class BackendSettings {
        private static final String DEFAULT_SOURCE_INDEXER = "javac";
        private static final String DEFAULT_CLASS_INDEXER = "asm";
        private static final String DEFAULT_COMPILER = "javac";

        private String sourceIndexer;
        private String classIndexer;
        private String compiler;

        public String getSourceIndexer() {
            return normalizeSourceIndexer(sourceIndexer, DEFAULT_SOURCE_INDEXER);
        }

        public String getClassIndexer() {
            return normalizeClassIndexer(classIndexer, DEFAULT_CLASS_INDEXER);
        }

        public String getCompiler() {
            return normalizeCompilerBackend(compiler, DEFAULT_COMPILER);
        }

        private static String normalizeSourceIndexer(String raw, String defaultValue) {
            if (raw == null || raw.isBlank()) return defaultValue;
            String n = raw.trim().toLowerCase(Locale.ROOT);
            return switch (n) {
                case "javac", "ecj", "turbine" -> n;
                default -> defaultValue;
            };
        }

        private static String normalizeCompilerBackend(String raw, String defaultValue) {
            if (raw == null || raw.isBlank()) return defaultValue;
            String n = raw.trim().toLowerCase(Locale.ROOT);
            return switch (n) {
                case "javac", "ecj" -> n;
                default -> defaultValue;
            };
        }

        private static String normalizeClassIndexer(String raw, String defaultValue) {
            if (raw == null || raw.isBlank()) return defaultValue;
            String n = raw.trim().toLowerCase(Locale.ROOT);
            return switch (n) {
                case "asm", "turbine" -> n;
                default -> defaultValue;
            };
        }
    }

    private static Optional<Declaration.Kind> kindFromString(String value) {
        if (value == null) return Optional.empty();
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "types" -> Optional.of(Declaration.Kind.TYPE);
            case "methods" -> Optional.of(Declaration.Kind.METHOD);
            case "fields" -> Optional.of(Declaration.Kind.FIELD);
            default -> Optional.empty();
        };
    }
}
