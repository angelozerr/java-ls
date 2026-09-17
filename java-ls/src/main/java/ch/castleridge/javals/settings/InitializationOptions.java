/**
 * Copyright 2026 by Anysphere Inc.
 *
 * Licensed under the MIT License.
 *
 * SPDX-License-Identifier: MIT
 *
 * Author: Thomas Mäder, Castle Ridge Software
 *
 */
package ch.castleridge.javals.settings;

import java.util.Optional;
import java.util.OptionalInt;

import ch.castleridge.javals.JsonUtils;

import org.eclipse.lsp4j.InitializeParams;

/**
 * Reads java-ls settings from LSP {@link InitializeParams#getInitializationOptions()}.
 * Delegates to {@link JavaLSSettings} for the POJO model and
 * {@link JsonUtils#toModel(Object, Class)} for deserialisation.
 */
public final class InitializationOptions {

    private InitializationOptions() {}

    /**
     * Deserialises the full {@link JavaLSSettings} from the initialization options.
     */
    public static JavaLSSettings settings(InitializeParams params) {
        Object options = params == null ? null : params.getInitializationOptions();
        JavaLSSettings settings = JsonUtils.toModel(options, JavaLSSettings.class);
        return settings != null ? settings : new JavaLSSettings();
    }

    public static OptionalInt referencesCandidateCap(InitializeParams params) {
        Integer cap = settings(params).getReferencesCandidateCap();
        return cap != null ? OptionalInt.of(cap) : OptionalInt.empty();
    }

    public static Optional<String> workspacePath(InitializeParams params) {
        String path = settings(params).getWorkspacePath();
        return path != null && !path.isBlank() ? Optional.of(path) : Optional.empty();
    }
}
