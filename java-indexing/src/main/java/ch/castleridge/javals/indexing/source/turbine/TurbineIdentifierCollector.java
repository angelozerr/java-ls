/**
 * Copyright 2026 by Anysphere Inc.
 *
 * Licensed under the MIT License.
 *
 * SPDX-License-Identifier: MIT
 *
 * Author: Thomas Mäder, Castle Ridge Software
 */
package ch.castleridge.javals.indexing.source.turbine;

import java.util.HashSet;
import java.util.Set;

import com.google.turbine.diag.SourceFile;
import com.google.turbine.parse.StreamLexer;
import com.google.turbine.parse.Token;
import com.google.turbine.parse.UnicodeEscapePreprocessor;

import ch.castleridge.javals.indexing.bloom.IdentifierBloomFilter;

/**
 * Lexes the complete source, including method bodies, and collects identifier
 * tokens for bloom-filter indexing.
 */
final class TurbineIdentifierCollector {

    private TurbineIdentifierCollector() {}

    static IdentifierBloomFilter collectAndBuild(String resourcePath, String source) {
        StreamLexer lexer = new StreamLexer(
                new UnicodeEscapePreprocessor(new SourceFile(resourcePath, source)));
        Set<String> names = new HashSet<>();
        for (Token token = lexer.next(); token != Token.EOF; token = lexer.next()) {
            if (token == Token.IDENT) {
                String name = lexer.stringValue();
                if (name != null && !name.isEmpty()) names.add(name);
            }
        }
        return IdentifierBloomFilter.create(names);
    }
}
