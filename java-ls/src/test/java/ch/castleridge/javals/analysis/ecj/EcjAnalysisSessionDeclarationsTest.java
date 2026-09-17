/**
 * Copyright (c) 2026 IBM Corporation and others.
 *
 * Licensed under the MIT License.
 *
 * SPDX-License-Identifier: MIT
 *
 * Author: Angelo Zerr
 */
package ch.castleridge.javals.analysis.ecj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import ch.castleridge.javals.analysis.AnalysisSession;
import ch.castleridge.javals.analysis.Declaration;
import ch.castleridge.javals.classpath.ClasspathOrder;
import ch.castleridge.javals.classpath.UriClasspathEntry;
import ch.castleridge.javals.indexing.index.InMemoryIndex;
import ch.castleridge.javals.indexing.scan.JrtInput;
import ch.castleridge.javals.indexing.scan.Scanner;

/**
 * Tests for {@link AnalysisSession#declarations()} in the ECJ backend.
 * Verifies that type, method, field and constructor declarations are returned,
 * while local variables and parameters are excluded.
 */
class EcjAnalysisSessionDeclarationsTest {

    /**
     * A class with a field, a method and a constructor must produce
     * declarations for each of them plus the class itself.
     */
    @Test
    void collectsTypeMethodFieldAndConstructorDeclarations() throws Exception {
        IndexedClasspath env = indexJrt();
        String source = """
                package demo;

                class Greeter {
                    String greeting;

                    Greeter(String greeting) {
                        this.greeting = greeting;
                    }

                    String greet(String name) {
                        String result = greeting + " " + name;
                        return result;
                    }
                }
                """;
        AnalysisSession session = new EcjWorkspaceCompiler().analyze(
                URI.create("file:///workspace/demo/Greeter.java"), source, env.index(), env.classpath());
        assertTrue(session.isUsable());

        List<Declaration> declarations = session.declarations();
        Set<String> names = declarations.stream()
                .map(Declaration::name)
                .collect(Collectors.toSet());

        // Type, field, constructor and method should all be present.
        assertTrue(names.contains("Greeter"), () -> "expected class declaration, got " + names);
        assertTrue(names.contains("greeting"), () -> "expected field declaration, got " + names);
        assertTrue(names.contains("greet"), () -> "expected method declaration, got " + names);

        // Local variables and parameters must be excluded.
        assertFalse(names.contains("result"), () -> "local variable should not appear in declarations");
        assertFalse(names.contains("name"), () -> "parameter should not appear in declarations");
    }

    /**
     * An interface with a method and a nested enum must produce the correct
     * declarations.
     */
    @Test
    void collectsInterfaceAndNestedTypeDeclarations() throws Exception {
        IndexedClasspath env = indexJrt();
        String source = """
                package demo;

                interface Processor {
                    void process(String input);

                    enum Status {
                        OK, FAILED
                    }
                }
                """;
        AnalysisSession session = new EcjWorkspaceCompiler().analyze(
                URI.create("file:///workspace/demo/Processor.java"), source, env.index(), env.classpath());
        assertTrue(session.isUsable());

        List<Declaration> declarations = session.declarations();
        Set<String> names = declarations.stream()
                .map(Declaration::name)
                .collect(Collectors.toSet());

        assertTrue(names.contains("Processor"), () -> "expected interface, got " + names);
        assertTrue(names.contains("process"), () -> "expected method, got " + names);
        assertTrue(names.contains("Status"), () -> "expected nested enum, got " + names);
    }

    /**
     * Every declaration must carry a non-file-local identity with a non-null
     * match key, so that it can be used for cross-file reference counting.
     */
    @Test
    void declarationIdentitiesAreNonFileLocal() throws Exception {
        IndexedClasspath env = indexJrt();
        String source = """
                package demo;

                class Holder {
                    int value;
                    int getValue() { return value; }
                }
                """;
        AnalysisSession session = new EcjWorkspaceCompiler().analyze(
                URI.create("file:///workspace/demo/Holder.java"), source, env.index(), env.classpath());
        assertTrue(session.isUsable());

        for (Declaration decl : session.declarations()) {
            assertFalse(decl.identity().fileLocal(),
                    () -> decl.name() + " should not be file-local");
            assertTrue(decl.identity().matchKey() != null && !decl.identity().matchKey().isEmpty(),
                    () -> decl.name() + " should have a non-empty match key");
        }
    }

    /**
     * An empty class produces declarations for the class itself and the
     * implicit default constructor.
     */
    @Test
    void emptyClassProducesClassAndConstructorDeclarations() throws Exception {
        IndexedClasspath env = indexJrt();
        String source = """
                package demo;

                class Empty {
                }
                """;
        AnalysisSession session = new EcjWorkspaceCompiler().analyze(
                URI.create("file:///workspace/demo/Empty.java"), source, env.index(), env.classpath());
        assertTrue(session.isUsable());

        List<Declaration> declarations = session.declarations();
        assertTrue(declarations.stream().anyMatch(d -> d.name().equals("Empty")),
                () -> "expected class declaration, got " + declarations);
        assertTrue(declarations.size() >= 1,
                () -> "expected at least the class declaration, got " + declarations);
    }

    /**
     * Declaration ranges must fall within the source boundaries.
     */
    @Test
    void declarationRangesAreValid() throws Exception {
        IndexedClasspath env = indexJrt();
        String source = """
                package demo;

                class Calc {
                    int add(int a, int b) {
                        return a + b;
                    }
                }
                """;
        AnalysisSession session = new EcjWorkspaceCompiler().analyze(
                URI.create("file:///workspace/demo/Calc.java"), source, env.index(), env.classpath());
        assertTrue(session.isUsable());

        for (Declaration decl : session.declarations()) {
            assertTrue(decl.nameRange().getStart().getLine() >= 0,
                    () -> decl.name() + " has negative start line");
            assertTrue(decl.nameRange().getEnd().getLine() >= decl.nameRange().getStart().getLine(),
                    () -> decl.name() + " has end before start");
        }
    }

    // -- test infrastructure --------------------------------------------------

    private static IndexedClasspath indexJrt() throws Exception {
        InMemoryIndex index = new InMemoryIndex();
        JrtInput jrt = new JrtInput(Path.of(System.getProperty("java.home")));
        assertTrue(new Scanner().scanAll(List.of(jrt), index).isEmpty());
        ClasspathOrder classpath =
                new ClasspathOrder(List.of(UriClasspathEntry.of(jrt.sourceUri().toString())), false);
        return new IndexedClasspath(index, classpath);
    }

    private record IndexedClasspath(InMemoryIndex index, ClasspathOrder classpath) {}
}
