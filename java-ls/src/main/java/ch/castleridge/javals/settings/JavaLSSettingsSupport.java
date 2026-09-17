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

import java.util.Set;
import java.util.function.Supplier;

import ch.castleridge.javals.analysis.Declaration;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;

/**
 * Centralises settings lifecycle: initial reading from
 * {@code initializationOptions}, handling {@code workspace/didChangeConfiguration},
 * and sending {@code workspace/codeLens/refresh} when needed.
 *
 * <p>Other services (e.g. {@code JavaTextDocumentService}) read current
 * settings directly via getters instead of listener callbacks.</p>
 */
public class JavaLSSettingsSupport {

    private final Supplier<LanguageClient> clientSupplier;
    private volatile boolean codeLensRefreshSupported;
    private volatile Set<Declaration.Kind> codeLensShowReferences = JavaLSSettings.ALL_CODELENS_KINDS;
    private volatile int referencesCandidateCap;

    public JavaLSSettingsSupport(Supplier<LanguageClient> clientSupplier) {
        this.clientSupplier = clientSupplier;
    }

    // --- Getters for current settings ----------------------------------------

    /**
     * Returns which declaration kinds should show a "references" CodeLens.
     */
    public Set<Declaration.Kind> getCodeLensShowReferences() {
        return codeLensShowReferences;
    }

    /**
     * Returns the maximum number of candidate files to scan for
     * cross-file references. {@code <= 0} means no cap.
     */
    public int getReferencesCandidateCap() {
        return referencesCandidateCap;
    }

    // --- Lifecycle -----------------------------------------------------------

    /**
     * Reads initial settings from {@code initializationOptions} and
     * detects client capabilities. Called once during {@code initialize}.
     */
    public JavaLSSettings initialize(InitializeParams params) {
        this.codeLensRefreshSupported = supportsCodeLensRefresh(params);
        JavaLSSettings settings = InitializationOptions.settings(params);
        updateCodeLens(settings);
        updateReferencesCandidateCap(settings);
        return settings;
    }

    /**
     * Handles {@code workspace/didChangeConfiguration}. Deserialises
     * the raw settings and applies each dynamic setting.
     */
    public void didChangeConfiguration(Object rawSettings) {
        JavaLSSettings settings = JavaLSSettings.fromSettings(rawSettings);
        updateCodeLens(settings);
    }

    // --- Per-setting update methods ------------------------------------------

    /**
     * Updates CodeLens settings (currently "showReferences" kinds).
     * Sends {@code workspace/codeLens/refresh} when the client supports it.
     */
    private void updateCodeLens(JavaLSSettings settings) {
        if (settings.getCodeLens() == null) {
            return;
        }
        this.codeLensShowReferences = settings.getCodeLensShowReferences();
        refreshCodeLensIfSupported();
    }

    private void updateReferencesCandidateCap(JavaLSSettings settings) {
        Integer cap = settings.getReferencesCandidateCap();
        if (cap != null) {
            this.referencesCandidateCap = cap;
        }
    }

    private void refreshCodeLensIfSupported() {
        if (codeLensRefreshSupported) {
            LanguageClient client = clientSupplier.get();
            if (client != null) {
                client.refreshCodeLenses();
            }
        }
    }

    private static boolean supportsCodeLensRefresh(InitializeParams params) {
        if (params == null || params.getCapabilities() == null) return false;
        WorkspaceClientCapabilities workspace = params.getCapabilities().getWorkspace();
        if (workspace == null || workspace.getCodeLens() == null) return false;
        return Boolean.TRUE.equals(workspace.getCodeLens().getRefreshSupport());
    }
}
