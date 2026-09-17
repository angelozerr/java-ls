/**
 * Copyright (c) 2026 IBM Corporation and others.
 *
 * Licensed under the MIT License.
 *
 * SPDX-License-Identifier: MIT
 *
 * Author: Angelo Zerr
 */
package ch.castleridge.javals;

import java.io.IOException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * Converts LSP settings objects into typed POJOs. LSP clients may send
 * settings as either a {@link java.util.Map} or a {@link com.google.gson.JsonObject};
 * this utility normalises both representations through Gson.
 */
public final class JsonUtils {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapterFactory(new FaultTolerantTypeAdapterFactory())
            .create();

    private JsonUtils() {}

    /**
     * Converts an arbitrary settings object to a typed model.
     * Handles {@code null}, already-typed instances, Gson {@link JsonElement}s,
     * and {@link java.util.Map}s transparently.
     */
    public static <T> T toModel(Object object, Class<T> clazz) {
        if (object == null) {
            return null;
        }
        if (clazz.isInstance(object)) {
            return clazz.cast(object);
        }
        JsonElement json = object instanceof JsonElement el ? el : GSON.toJsonTree(object);
        return GSON.fromJson(json, clazz);
    }

    /**
     * Gson {@link TypeAdapterFactory} that silently returns {@code null} when a
     * value cannot be deserialised (e.g. a string where an integer is expected).
     * Prevents a single malformed setting from crashing the whole server.
     *
     * <p>Inspired by LemMinX's {@code FaultTolerantTypeAdapterFactory}.</p>
     */
    private static final class FaultTolerantTypeAdapterFactory implements TypeAdapterFactory {

        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
            return new TypeAdapter<T>() {
                @Override
                public void write(JsonWriter out, T value) throws IOException {
                    delegate.write(out, value);
                }

                @Override
                public T read(JsonReader in) throws IOException {
                    try {
                        return delegate.read(in);
                    } catch (Exception e) {
                        in.skipValue();
                        return null;
                    }
                }
            };
        }
    }
}
