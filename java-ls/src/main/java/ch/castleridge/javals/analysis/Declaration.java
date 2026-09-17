/**
 * Copyright (c) 2026 IBM Corporation and others.
 *
 * Licensed under the MIT License.
 *
 * SPDX-License-Identifier: MIT
 *
 * Author: Angelo Zerr
 */
package ch.castleridge.javals.analysis;

import org.eclipse.lsp4j.Range;

/**
 * A named declaration (type, method, or field) in a compilation unit,
 * carrying enough information to count cross-file references via
 * {@link AnalysisSession#findReferencesTo(SymbolIdentity)}.
 */
public record Declaration(String name, Range nameRange, SymbolIdentity identity, Kind kind) {

    /**
     * The kind of declaration, used to filter which declarations
     * show a "references" CodeLens.
     */
    public enum Kind {
        TYPE, METHOD, FIELD
    }

    public Declaration {
        java.util.Objects.requireNonNull(name, "name");
        java.util.Objects.requireNonNull(nameRange, "nameRange");
        java.util.Objects.requireNonNull(identity, "identity");
        java.util.Objects.requireNonNull(kind, "kind");
    }
}
