/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.store.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.HashMap;

/** Utility class to create instances from class objects and checking failure reasons. */
public final class InstantiationUtil {

    public static <T> T deserializeObject(byte[] bytes, ClassLoader cl)
            throws IOException, ClassNotFoundException {

        return deserializeObject(new ByteArrayInputStream(bytes), cl);
    }

    @SuppressWarnings("unchecked")
    public static <T> T deserializeObject(InputStream in, ClassLoader cl)
            throws IOException, ClassNotFoundException {

        final ClassLoader old = Thread.currentThread().getContextClassLoader();
        // not using resource try to avoid AutoClosable's close() on the given stream
        try {
            ObjectInputStream oois = new InstantiationUtil.ClassLoaderObjectInputStream(in, cl);
            Thread.currentThread().setContextClassLoader(cl);
            return (T) oois.readObject();
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    public static byte[] serializeObject(Object o) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(o);
            oos.flush();
            return baos.toByteArray();
        }
    }

    /**
     * Clones the given serializable object using Java serialization.
     *
     * @param obj Object to clone
     * @param <T> Type of the object to clone
     * @return The cloned object
     * @throws IOException Thrown if the serialization or deserialization process fails.
     * @throws ClassNotFoundException Thrown if any of the classes referenced by the object cannot
     *     be resolved during deserialization.
     */
    public static <T extends Serializable> T clone(T obj)
            throws IOException, ClassNotFoundException {
        if (obj == null) {
            return null;
        } else {
            return clone(obj, obj.getClass().getClassLoader());
        }
    }

    /**
     * Clones the given serializable object using Java serialization, using the given classloader to
     * resolve the cloned classes.
     *
     * @param obj Object to clone
     * @param classLoader The classloader to resolve the classes during deserialization.
     * @param <T> Type of the object to clone
     * @return Cloned object
     * @throws IOException Thrown if the serialization or deserialization process fails.
     * @throws ClassNotFoundException Thrown if any of the classes referenced by the object cannot
     *     be resolved during deserialization.
     */
    public static <T extends Serializable> T clone(T obj, ClassLoader classLoader)
            throws IOException, ClassNotFoundException {
        if (obj == null) {
            return null;
        } else {
            final byte[] serializedObject = serializeObject(obj);
            return deserializeObject(serializedObject, classLoader);
        }
    }

    /** A custom ObjectInputStream that can load classes using a specific ClassLoader. */
    public static class ClassLoaderObjectInputStream extends ObjectInputStream {

        protected final ClassLoader classLoader;

        public ClassLoaderObjectInputStream(InputStream in, ClassLoader classLoader)
                throws IOException {
            super(in);
            this.classLoader = classLoader;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc)
                throws IOException, ClassNotFoundException {
            if (classLoader != null) {
                String name = desc.getName();
                try {
                    return Class.forName(name, false, classLoader);
                } catch (ClassNotFoundException ex) {
                    // check if class is a primitive class
                    Class<?> cl = primitiveClasses.get(name);
                    if (cl != null) {
                        // return primitive class
                        return cl;
                    } else {
                        // throw ClassNotFoundException
                        throw ex;
                    }
                }
            }

            return super.resolveClass(desc);
        }

        @Override
        protected Class<?> resolveProxyClass(String[] interfaces)
                throws IOException, ClassNotFoundException {
            if (classLoader != null) {
                ClassLoader nonPublicLoader = null;
                boolean hasNonPublicInterface = false;

                // define proxy in class loader of non-public interface(s), if any
                Class<?>[] classObjs = new Class<?>[interfaces.length];
                for (int i = 0; i < interfaces.length; i++) {
                    Class<?> cl = Class.forName(interfaces[i], false, classLoader);
                    if ((cl.getModifiers() & Modifier.PUBLIC) == 0) {
                        if (hasNonPublicInterface) {
                            if (nonPublicLoader != cl.getClassLoader()) {
                                throw new IllegalAccessError(
                                        "conflicting non-public interface class loaders");
                            }
                        } else {
                            nonPublicLoader = cl.getClassLoader();
                            hasNonPublicInterface = true;
                        }
                    }
                    classObjs[i] = cl;
                }
                try {
                    return Proxy.getProxyClass(
                            hasNonPublicInterface ? nonPublicLoader : classLoader, classObjs);
                } catch (IllegalArgumentException e) {
                    throw new ClassNotFoundException(null, e);
                }
            }

            return super.resolveProxyClass(interfaces);
        }

        // ------------------------------------------------

        private static final HashMap<String, Class<?>> primitiveClasses = new HashMap<>(9);

        static {
            primitiveClasses.put("boolean", boolean.class);
            primitiveClasses.put("byte", byte.class);
            primitiveClasses.put("char", char.class);
            primitiveClasses.put("short", short.class);
            primitiveClasses.put("int", int.class);
            primitiveClasses.put("long", long.class);
            primitiveClasses.put("float", float.class);
            primitiveClasses.put("double", double.class);
            primitiveClasses.put("void", void.class);
        }
    }

    // --------------------------------------------------------------------------------------------

    /** Private constructor to prevent instantiation. */
    private InstantiationUtil() {
        throw new RuntimeException();
    }
}
