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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import ch.castleridge.javals.indexing.IndexTestUtils;
import ch.castleridge.javals.indexing.bloom.BloomEntry;
import ch.castleridge.javals.indexing.bloom.IdentifierBloomFilter;
import ch.castleridge.javals.indexing.index.InMemoryIndex;
import ch.castleridge.javals.indexing.index.Index;
import ch.castleridge.javals.indexing.model.SourceTypeEntry;
import ch.castleridge.javals.indexing.model.Type;
import ch.castleridge.javals.indexing.model.TypeDeclKind;
import ch.castleridge.javals.indexing.model.TypeRef;
import ch.castleridge.javals.indexing.source.SourceIndexer;

class TurbineSourceIndexerSmokeTest {
    private static final String RESOURCE_PATH = "p/Hello.java";
    private static final String SOURCE_URI = "index:///source/";

    @Test
    void indexesHeadersAndLexesMethodBodies() {
        Index index = new InMemoryIndex();
        TurbineSourceIndexer.index(
                RESOURCE_PATH,
                SOURCE_URI,
                """
                        package p;

                        import java.util.List;

                        @Deprecated
                        public class Hello<T extends Number> implements Runnable {
                            public static final int ANSWER = 42;
                            protected List<T> values;

                            private Hello() {}

                            @Override
                            public void run() {
                                System.out.println(values);
                            }

                            static class Nested {}
                        }
                        """,
                index);

        SourceTypeEntry hello = (SourceTypeEntry) IndexTestUtils.get(index, "p/Hello");
        assertNotNull(hello);
        assertEquals(TypeDeclKind.CLASS, hello.declKind());
        assertTrue((hello.modifiers() & Opcodes.ACC_PUBLIC) != 0);
        assertEquals("T", hello.typeParams()[0].name());
        assertInstanceOf(TypeRef.Unresolved.class, hello.typeParams()[0].bounds()[0]);
        assertEquals("Runnable", ((TypeRef.Unresolved) hello.interfaceRefs()[0]).simpleName());

        assertTrue(Arrays.stream(hello.fields())
                .anyMatch(field -> field.name().equals("ANSWER") && Integer.valueOf(42).equals(field.constantValue())));
        Type valuesType = Arrays.stream(hello.fields())
                .filter(field -> field.name().equals("values"))
                .findFirst()
                .orElseThrow()
                .type();
        assertInstanceOf(Type.Parameterized.class, valuesType);
        assertTrue(Arrays.stream(hello.methods()).anyMatch(method -> method.name().equals("run")));
        assertTrue(Arrays.stream(hello.methods()).anyMatch(method -> method.name().equals("<init>")));
        assertNotNull(IndexTestUtils.get(index, "p/Hello$Nested"));

        IdentifierBloomFilter bloom = index.bloomFilters().stream()
                .filter(entry -> RESOURCE_PATH.equals(entry.resourcePath()))
                .map(BloomEntry::filter)
                .findFirst()
                .orElseThrow();
        assertTrue(bloom.mightContain("println"));
        assertTrue(bloom.mightContain("values"));
        assertFalse(bloom.mightContain("definitelyNotInThisFile"));
    }

    @Test
    void factorySelectsTurbine() {
        assertSame(TurbineSourceIndexer.INSTANCE, SourceIndexer.turbine());
        assertSame(TurbineSourceIndexer.INSTANCE, SourceIndexer.of("TURBINE"));
    }

    @Test
    void preservesImplicitInterfaceAndEnumFlags() {
        Index index = new InMemoryIndex();
        TurbineSourceIndexer.index(
                "p/Api.java",
                SOURCE_URI,
                """
                        package p;

                        public interface Api {
                            int VALUE = 1;
                            void call();
                            default void implemented() {}
                        }

                        enum Choice { YES }
                        """,
                index);

        SourceTypeEntry api = (SourceTypeEntry) IndexTestUtils.get(index, "p/Api");
        var value = Arrays.stream(api.fields()).filter(field -> field.name().equals("VALUE")).findFirst().orElseThrow();
        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                value.modifiers() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL));
        var call = Arrays.stream(api.methods()).filter(method -> method.name().equals("call")).findFirst().orElseThrow();
        assertTrue((call.modifiers() & Opcodes.ACC_ABSTRACT) != 0);
        assertFalse(call.hasBody());
        var implemented = Arrays.stream(api.methods())
                .filter(method -> method.name().equals("implemented"))
                .findFirst()
                .orElseThrow();
        assertTrue(implemented.hasBody());

        SourceTypeEntry choice = (SourceTypeEntry) IndexTestUtils.get(index, "p/Choice");
        var yes = Arrays.stream(choice.fields()).filter(field -> field.name().equals("YES")).findFirst().orElseThrow();
        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM,
                yes.modifiers()
                        & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM));
    }
}
