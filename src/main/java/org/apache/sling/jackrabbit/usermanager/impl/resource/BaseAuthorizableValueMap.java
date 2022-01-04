/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.jackrabbit.usermanager.impl.resource;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.Binary;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.jackrabbit.usermanager.resource.SystemUserManagerPaths;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * base implementation for ValueMap implementations for Authorizable Resources
 */
public abstract class BaseAuthorizableValueMap implements ValueMap {

    /**
     * default log
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected boolean fullyRead;
    protected final Map<String, Object> cache;
    protected Authorizable authorizable;
    protected final SystemUserManagerPaths systemUserManagerPaths;

    protected BaseAuthorizableValueMap(Authorizable authorizable, SystemUserManagerPaths systemUserManagerPaths) {
        this.authorizable = authorizable;
        this.cache = new LinkedHashMap<>();
        this.fullyRead = false;
        this.systemUserManagerPaths = systemUserManagerPaths;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String name, Class<T> type) {
        if (type == null) {
            return (T) get(name);
        }

        return convertToType(name, type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String name, T defaultValue) {
        if (defaultValue == null) {
            return (T) get(name);
        }

        // special handling in case the default value implements one
        // of the interface types supported by the convertToType method
        Class<T> type = (Class<T>) normalizeClass(defaultValue.getClass());

        T value = get(name, type);
        if (value == null) {
            value = defaultValue;
        }

        return value;
    }

    public boolean containsKey(Object key) {
        return get(key) != null;
    }

    public boolean containsValue(Object value) {
        readFully();
        return cache.containsValue(value);
    }

    public Set<java.util.Map.Entry<String, Object>> entrySet() {
        readFully();
        return cache.entrySet();
    }

    public Object get(Object key) {
        Object value = cache.get(key);
        if (value == null) {
            value = read((String) key);
        }

        return value;
    }

    public Set<String> keySet() {
        readFully();
        return cache.keySet();
    }

    public int size() {
        readFully();
        return cache.size();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public Collection<Object> values() {
        readFully();
        return cache.values();
    }

    protected abstract Object read(String key);

    protected Object readPropertyAndCache(String key, String relPath) throws RepositoryException {
        Value[] property = authorizable.getProperty(relPath);
        Object value = valuesToJavaObject(property);
        cache.put(key, value);
        return value;
    }

    /**
     * Converts a JCR Value to a corresponding Java Object
     *
     * @param value the JCR Value to convert
     * @return the Java Object
     * @throws RepositoryException if the value cannot be converted
     */
    public static Object toJavaObject(Value value) throws RepositoryException {
        switch (value.getType()) {
            case PropertyType.DECIMAL:
                return value.getDecimal();
            case PropertyType.BINARY:
                return new LazyInputStream(value);
            case PropertyType.BOOLEAN:
                return value.getBoolean();
            case PropertyType.DATE:
                return value.getDate();
            case PropertyType.DOUBLE:
                return value.getDouble();
            case PropertyType.LONG:
                return value.getLong();
            case PropertyType.NAME: // fall through
            case PropertyType.PATH: // fall through
            case PropertyType.REFERENCE: // fall through
            case PropertyType.STRING: // fall through
            case PropertyType.UNDEFINED: // not actually expected
            default: // not actually expected
                return value.getString();
        }
    }
    protected Object valuesToJavaObject(Value[] values)
            throws RepositoryException {
        if (values == null) {
            return null;
        } else if (values.length == 1) {
            return toJavaObject(values[0]);
        } else {
            Object[] valuesObjs = new Object[values.length];
            for (int i = 0; i < values.length; i++) {
                valuesObjs[i] = toJavaObject(values[i]);
            }
            return valuesObjs;
        }
    }

    protected abstract void readFully();

    /**
     * Reads the authorizable map completely and returns the string
     * representation of the cached properties.
     */
    @Override
    public String toString() {
        readFully();
        return cache.toString();
    }

    // ---------- Unsupported Modification methods

    public Object remove(Object arg0) {
        throw new UnsupportedOperationException();
    }

    public void clear() {
        throw new UnsupportedOperationException();
    }

    public Object put(String arg0, Object arg1) {
        throw new UnsupportedOperationException();
    }

    public void putAll(Map<? extends String, ? extends Object> arg0) {
        throw new UnsupportedOperationException();
    }

    // ---------- Implementation helper

    @SuppressWarnings("unchecked")
    protected <T> T convertToType(String name, Class<T> type) {
        T result = null;

        try {
            if (authorizable.hasProperty(name)) {
                Value[] values = authorizable.getProperty(name);

                if (values == null) {
                    return null;
                }

                boolean multiValue = values.length > 1;
                boolean array = type.isArray();

                if (multiValue) {
                    if (array) {
                        result = (T) convertToArray(values,
                            type.getComponentType());
                    } else if (values.length > 0) {
                        result = convertToType(values[0], type);
                    }
                } else {
                    Value value = values[0];
                    if (array) {
                        result = (T) convertToArray(new Value[] { value },
                            type.getComponentType());
                    } else {
                        result = convertToType(value, type);
                    }
                }
            } else {
                // some synthetic property not stored with the authorizable?
                //  fallback to the default impl from the ValueMap interface
                result = ValueMap.super.get(name, type);
            }
        } catch (ValueFormatException vfe) {
            log.info(String.format("convertToType: Cannot convert value of %s to %s", name, type), vfe);
        } catch (RepositoryException re) {
            log.info(String.format("convertToType: Cannot get value of %s", name), re);
        }

        // fall back to nothing
        return result;
    }

    private <T> T[] convertToArray(Value[] jcrValues, Class<T> type)
            throws RepositoryException {
        // lazy create this list in case there are no valid type conversions
        List<T> values = null;
        for (int i = 0; i < jcrValues.length; i++) {
            T value = convertToType(jcrValues[i], type);
            if (value != null) {
                if (values == null) {
                    values = new ArrayList<>();
                }
                values.add(value);
            }
        }

        T[] array = null;
        if (values != null) {
            @SuppressWarnings("unchecked")
            T[] result = (T[]) Array.newInstance(type, values.size());
            array = values.toArray(result);
        }
        return array;
    }

    @SuppressWarnings("unchecked")
    private <T> T convertToType(Value jcrValue, Class<T> type)
            throws RepositoryException {

        if (String.class == type) {
            return (T) jcrValue.getString();
        } else if (Byte.class == type) {
            return (T) Byte.valueOf((byte) jcrValue.getLong());
        } else if (BigDecimal.class == type) {
            return (T) jcrValue.getDecimal();
        } else if (Short.class == type) {
            return (T) Short.valueOf((short) jcrValue.getLong());
        } else if (Integer.class == type) {
            return (T) Integer.valueOf((int) jcrValue.getLong());
        } else if (Long.class == type) {
            return (T) Long.valueOf(jcrValue.getLong());
        } else if (Float.class == type) {
            return (T) Float.valueOf((float)jcrValue.getDouble());
        } else if (Double.class == type) {
            return (T) Double.valueOf(jcrValue.getDouble());
        } else if (Boolean.class == type) {
            return (T) Boolean.valueOf(jcrValue.getBoolean());
        } else if (Date.class == type) {
            return (T) jcrValue.getDate().getTime();
        } else if (Calendar.class == type) {
            return (T) jcrValue.getDate();
        } else if (Binary.class == type) {
            return (T) jcrValue.getBinary();
        } else if (InputStream.class == type) {
            return (T) jcrValue.getBinary().getStream();
        } else if (Value.class == type) {
            return (T) jcrValue;
        }

        // fallback in case of unsupported type
        return null;
    }

    private Class<?> normalizeClass(Class<?> type) {
        if (Calendar.class.isAssignableFrom(type)) {
            type = Calendar.class;
        } else if (Date.class.isAssignableFrom(type)) {
            type = Date.class;
        } else if (Value.class.isAssignableFrom(type)) {
            type = Value.class;
        } else if (InputStream.class.isAssignableFrom(type)) {
            type = InputStream.class;
        } else if (Binary.class.isAssignableFrom(type)) {
            type = Binary.class;
        }
        return type;
    }

    public static class LazyInputStream extends InputStream {

        /** The JCR Value from which the input stream is requested on demand */
        private final Value value;

        /** The inputstream created on demand, null if not used */
        private InputStream delegatee;

        public LazyInputStream(@NotNull Value value) {
            this.value = value;
        }

        /**
         * Closes the input stream if acquired otherwise does nothing.
         */
        @Override
        public void close() throws IOException {
            if (delegatee != null) {
                delegatee.close();
            }
        }

        @Override
        public int available() throws IOException {
            return getStream().available();
        }

        @Override
        public int read() throws IOException {
            return getStream().read();
        }

        @Override
        public int read(byte[] b) throws IOException {
            return getStream().read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return getStream().read(b, off, len);
        }

        @Override
        public long skip(long n) throws IOException {
            return getStream().skip(n);
        }

        @Override
        public boolean markSupported() {
            try {
                return getStream().markSupported();
            } catch (IOException ioe) {
                // ignore
            }
            return false;
        }

        @Override
        public synchronized void mark(int readlimit) {
            try {
                getStream().mark(readlimit);
            } catch (IOException ioe) {
                // ignore
            }
        }

        @Override
        public synchronized void reset() throws IOException {
            getStream().reset();
        }

        /** Actually retrieves the input stream from the underlying JCR Value */
        private InputStream getStream() throws IOException {
            if (delegatee == null) {
                try {
                    delegatee = value.getBinary().getStream();
                } catch (RepositoryException re) {
                    throw (IOException) new IOException(re.getMessage()).initCause(re);
                }
            }
            return delegatee;
        }

    }
}