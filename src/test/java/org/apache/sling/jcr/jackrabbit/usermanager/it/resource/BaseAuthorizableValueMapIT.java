/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.jcr.jackrabbit.usermanager.it.resource;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import javax.jcr.Binary;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.util.ISO8601;
import org.apache.jackrabbit.value.ValueFactoryImpl;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.junit.Before;
import org.junit.Test;

/**
 * Basic test of AuthorizableValueMap
 */
public abstract class BaseAuthorizableValueMapIT extends BaseAuthorizableResourcesIT {

    protected Calendar NOW = Calendar.getInstance();
    protected String uuid;
    protected String uuid2;

    @Before
    public void setup() throws RepositoryException, LoginException {
        super.setup();

        // clear out the milliseconds field which isn't 
        // relevant for date property values
        NOW.set(Calendar.MILLISECOND, 0);

        uuid = UUID.randomUUID().toString();
        uuid2 = UUID.randomUUID().toString();

        Map<String, Object> props = createAuthorizableProps();

        user1 = createUser.createUser(adminSession, createUniqueName("user"), "testPwd", "testPwd",
                props, new ArrayList<>());
        assertNotNull("Expected user1 to not be null", user1);

        Map<String, Object> groupProps = new HashMap<>(props);
        groupProps.put(":member", user1.getID());
        group1 = createGroup.createGroup(adminSession, createUniqueName("group"),
                groupProps, new ArrayList<>());
        assertNotNull("Expected group1 to not be null", group1);

        if (adminSession.hasPendingChanges()) {
            adminSession.save();
        }
    }

    protected Map<String, Object> createAuthorizableProps() throws LoginException {
        return createAuthorizableProps("");
    }
    protected Map<String, Object> createAuthorizableProps(String prefix) throws LoginException {
        Map<String, Object> props = new HashMap<>();
        props.put(String.format("%skey1", prefix), "value1");

        props.put(String.format("%sstring1@TypeHint", prefix), PropertyType.TYPENAME_STRING);
        props.put(String.format("%sstring1", prefix), "value1");

        props.put(String.format("%sstring2@TypeHint", prefix), toMultivalueTypeHint(PropertyType.TYPENAME_STRING));
        props.put(String.format("%sstring2", prefix), new String[] {"value1", "value2"});

        props.put(String.format("%sbinary1@TypeHint", prefix), PropertyType.TYPENAME_BINARY);
        props.put(String.format("%sbinary1", prefix), "value1");

        props.put(String.format("%sbinary2@TypeHint", prefix), toMultivalueTypeHint(PropertyType.TYPENAME_BINARY));
        props.put(String.format("%sbinary2", prefix), new String[] {"value1", "value2"});

        props.put(String.format("%sboolean1@TypeHint", prefix), PropertyType.TYPENAME_BOOLEAN);
        props.put(String.format("%sboolean1", prefix), "false");

        props.put(String.format("%sboolean2@TypeHint", prefix), toMultivalueTypeHint(PropertyType.TYPENAME_BOOLEAN));
        props.put(String.format("%sboolean2", prefix), new String[] {"false", "true"});

        props.put(String.format("%slong1@TypeHint", prefix), PropertyType.TYPENAME_LONG);
        props.put(String.format("%slong1", prefix), "1");

        props.put(String.format("%slong2@TypeHint", prefix), toMultivalueTypeHint(PropertyType.TYPENAME_LONG));
        props.put(String.format("%slong2", prefix), new String[] {"1", "2"});

        props.put(String.format("%sdouble1@TypeHint", prefix), PropertyType.TYPENAME_DOUBLE);
        props.put(String.format("%sdouble1", prefix), "1.1");

        props.put(String.format("%sdouble2@TypeHint", prefix), toMultivalueTypeHint(PropertyType.TYPENAME_DOUBLE));
        props.put(String.format("%sdouble2", prefix), new String[] {"1.1", "2.2"});

        props.put(String.format("%sdecimal1@TypeHint", prefix), PropertyType.TYPENAME_DECIMAL);
        props.put(String.format("%sdecimal1", prefix), "1");

        props.put(String.format("%sdecimal2@TypeHint", prefix), toMultivalueTypeHint(PropertyType.TYPENAME_DECIMAL));
        props.put(String.format("%sdecimal2", prefix), new String[] {"1", "2"});

        props.put(String.format("%sdate1@TypeHint", prefix), PropertyType.TYPENAME_DATE);
        props.put(String.format("%sdate1", prefix), ISO8601.format(NOW));

        props.put(String.format("%sdate2@TypeHint", prefix), toMultivalueTypeHint(PropertyType.TYPENAME_DATE));
        props.put(String.format("%sdate2", prefix), new String[] {ISO8601.format(NOW), ISO8601.format(NOW)});

        props.put(String.format("%sname1@TypeHint", prefix), PropertyType.TYPENAME_NAME);
        props.put(String.format("%sname1", prefix), "name1");

        props.put(String.format("%sname2@TypeHint", prefix), toMultivalueTypeHint(PropertyType.TYPENAME_NAME));
        props.put(String.format("%sname2", prefix), new String[] {"name1", "name2"});

        props.put(String.format("%spath1@TypeHint", prefix), PropertyType.TYPENAME_PATH);
        props.put(String.format("%spath1", prefix), "/content");

        props.put(String.format("%spath2@TypeHint", prefix), toMultivalueTypeHint(PropertyType.TYPENAME_PATH));
        props.put(String.format("%spath2", prefix), new String [] {"/content", "/home"});

        props.put(String.format("%sreference1@TypeHint", prefix), PropertyType.TYPENAME_REFERENCE);
        props.put(String.format("%sreference1", prefix), uuid);

        props.put(String.format("%sreference2@TypeHint", prefix), toMultivalueTypeHint(PropertyType.TYPENAME_REFERENCE));
        props.put(String.format("%sreference2", prefix), new String[] {uuid, uuid});

        props.put(String.format("%sweakreference1@TypeHint", prefix), PropertyType.TYPENAME_WEAKREFERENCE);
        props.put(String.format("%sweakreference1", prefix), uuid);

        props.put(String.format("%sweakreference2@TypeHint", prefix), toMultivalueTypeHint(PropertyType.TYPENAME_WEAKREFERENCE));
        props.put(String.format("%sweakreference2", prefix), new String[] {uuid, uuid});

        props.put(String.format("%suri1@TypeHint", prefix), PropertyType.TYPENAME_URI);
        props.put(String.format("%suri1", prefix), "http://localhost:8080/content");

        props.put(String.format("%suri2@TypeHint", prefix), toMultivalueTypeHint(PropertyType.TYPENAME_URI));
        props.put(String.format("%suri2", prefix), new String [] {"http://localhost:8080/content", "http://localhost:8080/home"});

        props.put(String.format("%sundefined1@TypeHint", prefix), PropertyType.TYPENAME_UNDEFINED);
        props.put(String.format("%sundefined1", prefix), "value1");

        props.put(String.format("%sundefined2@TypeHint", prefix), toMultivalueTypeHint(PropertyType.TYPENAME_UNDEFINED));
        props.put(String.format("%sundefined2", prefix), new String [] {"value1", "value2"});

        return props;
    }

    protected String toMultivalueTypeHint(String typeNameString) {
        return String.format("%s[]", typeNameString);
    }

    @Test
    public void testGetStringClassOfT() throws LoginException, RepositoryException, IOException {
        ValueMap vm = getValueMap(user1);
        String vmValue1 = vm.get("key1", String.class);
        assertEquals("value1", vmValue1);

        Long vmValue2 = vm.get("key1", Long.class);
        assertNull(vmValue2);

        Object vmValue3 = vm.get("key1", (Class<?>)null);
        assertEquals("value1", vmValue3);

        String vmValue4 = vm.get("not_a_key1", String.class);
        assertNull(vmValue4);

        ValueMap vm2 = getValueMap(group1);
        String vm2Value1 = vm2.get("key1", String.class);
        assertEquals("value1", vm2Value1);

        Long vm2Value2 = vm2.get("key1", Long.class);
        assertNull(vm2Value2);

        Object vm2Value3 = vm2.get("key1", (Class<?>)null);
        assertEquals("value1", vm2Value3);

        String vm2Value4 = vm2.get("not_a_key1", String.class);
        assertNull(vm2Value4);


        assertEquals("value1", vm.get("string1", String.class));
        assertArrayEquals(new String[] {"value1", "value2"}, vm.get("string2", String[].class));

        // try converting single value to array
        assertArrayEquals(new String[] {"value1"}, vm.get("string1", String[].class));
        // try converting multi value to first item
        assertEquals("value1", vm.get("string2", String.class));
        // try some other type that has no conversion available
        assertNull(vm.get("string1", Object.class));
        assertNull(vm.get("string2", Object[].class));

        try (InputStream binary1 = vm.get("binary1", InputStream.class)) {
            String binary1asString = IOUtils.toString(binary1, StandardCharsets.UTF_8);
            assertEquals("value1", binary1asString);
        }
        InputStream[] binary2AsStream = vm.get("binary2", InputStream[].class);
        assertEquals(2, binary2AsStream.length);
        try (InputStream is = binary2AsStream[0]) {
            String binary2asString = IOUtils.toString(is, StandardCharsets.UTF_8);
            assertEquals("value1", binary2asString);
        }
        try (InputStream is = binary2AsStream[1]) {
            String binary2asString = IOUtils.toString(is, StandardCharsets.UTF_8);
            assertEquals("value2", binary2asString);
        }

        Binary binary1 = vm.get("binary1", Binary.class);
        try (InputStream is = binary1.getStream()) {
            String binary1asString = IOUtils.toString(is, StandardCharsets.UTF_8);
            assertEquals("value1", binary1asString);
        }
        Binary[] binary2AsBinary = vm.get("binary2", Binary[].class);
        assertEquals(2, binary2AsBinary.length);
        try (InputStream is = binary2AsBinary[0].getStream()) {
            String binary2asString = IOUtils.toString(is, StandardCharsets.UTF_8);
            assertEquals("value1", binary2asString);
        }
        try (InputStream is = binary2AsBinary[1].getStream()) {
            String binary2asString = IOUtils.toString(is, StandardCharsets.UTF_8);
            assertEquals("value2", binary2asString);
        }

        assertEquals(Boolean.FALSE, vm.get("boolean1", Boolean.class));
        assertArrayEquals(new Boolean[] {Boolean.FALSE, Boolean.TRUE}, vm.get("boolean2", Boolean[].class));

        assertEquals(Long.valueOf(1), vm.get("long1", Long.class));
        assertArrayEquals(new Long[] {1L, 2L}, vm.get("long2", Long[].class));

        // other types of numbers
        assertEquals(Short.valueOf((short)1), vm.get("long1", Short.class));
        assertArrayEquals(new Short[] {1, 2}, vm.get("long2", Short[].class));
        assertEquals(Integer.valueOf(1), vm.get("long1", Integer.class));
        assertArrayEquals(new Integer[] {1, 2}, vm.get("long2", Integer[].class));
        assertEquals(Byte.valueOf((byte)1), vm.get("long1", Byte.class));
        assertArrayEquals(new Byte[] {1, 2}, vm.get("long2", Byte[].class));

        assertEquals(Double.valueOf(1.1), vm.get("double1", Double.class));
        assertArrayEquals(new Double[] {1.1, 2.2}, vm.get("double2", Double[].class));

        // other types of numbers
        assertEquals(Float.valueOf((float)1.1), vm.get("double1", Float.class));
        assertArrayEquals(new Float[] {(float)1.1, (float)2.2}, vm.get("double2", Float[].class));

        assertEquals(new BigDecimal(1), vm.get("decimal1", BigDecimal.class));
        assertArrayEquals(new BigDecimal[] {new BigDecimal(1), new BigDecimal(2)}, vm.get("decimal2", BigDecimal[].class));

        Calendar date1 = vm.get("date1", Calendar.class);
        assertEquals(ISO8601.format(NOW), ISO8601.format(date1));
        Calendar[] date2 = vm.get("date2", Calendar[].class);
        assertEquals(2, date2.length);
        assertEquals(ISO8601.format(NOW), ISO8601.format(date2[0]));
        assertEquals(ISO8601.format(NOW), ISO8601.format(date2[1]));

        // other types of date
        assertEquals(NOW.getTime(), vm.get("date1", Date.class));
        assertArrayEquals(new Date[] {NOW.getTime(), NOW.getTime()}, vm.get("date2", Date[].class));

        ValueFactory valueFactory = ValueFactoryImpl.getInstance();
        assertValueEquals(valueFactory.createValue("name1", PropertyType.NAME), vm.get("name1", Value.class));
        assertValueArrayEquals(new Value[] {valueFactory.createValue("name1", PropertyType.NAME), valueFactory.createValue("name2", PropertyType.NAME)}, vm.get("name2", Value[].class));

        assertValueEquals(valueFactory.createValue("/content", PropertyType.PATH), vm.get("path1", Value.class));
        assertValueArrayEquals(new Value[] {valueFactory.createValue("/content", PropertyType.PATH), valueFactory.createValue("/home", PropertyType.PATH)}, vm.get("path2", Value[].class));

        assertValueEquals(valueFactory.createValue(uuid, PropertyType.REFERENCE), vm.get("reference1", Value.class));
        assertValueArrayEquals(new Value[] {valueFactory.createValue(uuid, PropertyType.REFERENCE), valueFactory.createValue(uuid, PropertyType.REFERENCE)}, vm.get("reference2", Value[].class));

        assertValueEquals(valueFactory.createValue(uuid, PropertyType.WEAKREFERENCE), vm.get("weakreference1", Value.class));
        assertValueArrayEquals(new Value[] {valueFactory.createValue(uuid, PropertyType.WEAKREFERENCE), valueFactory.createValue(uuid, PropertyType.WEAKREFERENCE)}, vm.get("weakreference2", Value[].class));

        assertValueEquals(valueFactory.createValue("http://localhost:8080/content", PropertyType.URI), vm.get("uri1", Value.class));
        assertValueArrayEquals(new Value[] {valueFactory.createValue("http://localhost:8080/content", PropertyType.URI), valueFactory.createValue("http://localhost:8080/home", PropertyType.URI)}, vm.get("uri2", Value[].class));

        assertEquals("value1", vm.get("undefined1", String.class));
        assertArrayEquals(new String[] {"value1", "value2"}, vm.get("undefined2", String[].class));
    }

    @Test
    public void testGetStringT() throws LoginException, RepositoryException, IOException {
        ValueMap vm = getValueMap(user1);
        String vmValue1 = vm.get("key1", "default1");
        assertEquals("value1", vmValue1);
        String vmValue2 = vm.get("not_a_key1", "default1");
        assertEquals("default1", vmValue2);
        String vmValue3 = vm.get("key1", (String)null);
        assertEquals("value1", vmValue3);
        String vmValue4 = vm.get("not_a_key1", (String)null);
        assertNull(vmValue4);

        ValueMap vm2 = getValueMap(group1);
        String vm2Value1 = vm2.get("key1", "default1");
        assertEquals("value1", vm2Value1);
        String vm2Value2 = vm2.get("not_a_key1", "default1");
        assertEquals("default1", vm2Value2);
        String vm2Value3 = vm2.get("key1", (String)null);
        assertEquals("value1", vm2Value3);
        String vm2Value4 = vm2.get("not_a_key1", (String)null);
        assertNull(vm2Value4);


        assertEquals("value1", vm.get("string1", "default1"));
        assertEquals("default1", vm.get("string1a", "default1"));
        assertArrayEquals(new String[] {"value1", "value2"}, vm.get("string2", new String[] {"default1", "default2"}));
        assertArrayEquals(new String[] {"default1", "default2"}, vm.get("string2a", new String[] {"default1", "default2"}));

        // try converting single value to array
        assertArrayEquals(new String[] {"value1"}, vm.get("string1", new String[] {"default1"}));
        assertArrayEquals(new String[] {"default1"}, vm.get("string1a", new String[] {"default1"}));
        // try converting multi value to first item
        assertEquals("value1", vm.get("string2", "default1"));
        assertEquals("default1", vm.get("string2a", "default1"));
        // try some other type that has no conversion available
        Object defaultObj = new Object();
        assertEquals(defaultObj, vm.get("string1", defaultObj));
        Object[] defaultObjArray = new Object[] {defaultObj};
        assertArrayEquals(defaultObjArray, vm.get("string2", defaultObjArray));

        try (InputStream defaultIS = new ByteArrayInputStream("default1".getBytes());
                InputStream binary1 = vm.get("binary1", defaultIS)) {
            String binary1asString = IOUtils.toString(binary1, StandardCharsets.UTF_8);
            assertEquals("value1", binary1asString);
        }
        try (InputStream defaultIS = new ByteArrayInputStream("default1".getBytes());
                InputStream binary1 = vm.get("binary1a", defaultIS)) {
            String binary1asString = IOUtils.toString(binary1, StandardCharsets.UTF_8);
            assertEquals("default1", binary1asString);
        }
        try (InputStream defaultIS1 = new ByteArrayInputStream("default1".getBytes());
                InputStream defaultIS2 = new ByteArrayInputStream("default2".getBytes());) {
            InputStream[] binary2AsStream = vm.get("binary2", new InputStream[] {defaultIS1, defaultIS2});
            assertEquals(2, binary2AsStream.length);
            try (InputStream is = binary2AsStream[0]) {
                String binary2asString = IOUtils.toString(is, StandardCharsets.UTF_8);
                assertEquals("value1", binary2asString);
            }
            try (InputStream is = binary2AsStream[1]) {
                String binary2asString = IOUtils.toString(is, StandardCharsets.UTF_8);
                assertEquals("value2", binary2asString);
            }
        }
        try (InputStream defaultIS1 = new ByteArrayInputStream("default1".getBytes());
                InputStream defaultIS2 = new ByteArrayInputStream("default2".getBytes());) {
            InputStream[] binary2AsStream = vm.get("binary2a", new InputStream[] {defaultIS1, defaultIS2});
            assertEquals(2, binary2AsStream.length);
            try (InputStream is = binary2AsStream[0]) {
                String binary2asString = IOUtils.toString(is, StandardCharsets.UTF_8);
                assertEquals("default1", binary2asString);
            }
            try (InputStream is = binary2AsStream[1]) {
                String binary2asString = IOUtils.toString(is, StandardCharsets.UTF_8);
                assertEquals("default2", binary2asString);
            }
        }

        ValueFactory valueFactory = ValueFactoryImpl.getInstance();
        Binary defaultBinary1;
        try (InputStream is = new ByteArrayInputStream("default1".getBytes())) {
            defaultBinary1 = valueFactory.createBinary(is);
        }
        Binary defaultBinary2;
        try (InputStream is = new ByteArrayInputStream("default2".getBytes())) {
            defaultBinary2 = valueFactory.createBinary(is);
        }

        Binary binary1 = vm.get("binary1", defaultBinary1);
        try (InputStream is = binary1.getStream()) {
            String binary1asString = IOUtils.toString(is, StandardCharsets.UTF_8);
            assertEquals("value1", binary1asString);
        }
        Binary binary1a = vm.get("binary1a", defaultBinary1);
        try (InputStream is = binary1a.getStream()) {
            String binary1asString = IOUtils.toString(is, StandardCharsets.UTF_8);
            assertEquals("default1", binary1asString);
        }
        Binary[] binary2AsBinary = vm.get("binary2", new Binary[] {defaultBinary1, defaultBinary2});
        assertEquals(2, binary2AsBinary.length);
        try (InputStream is = binary2AsBinary[0].getStream()) {
            String binary2asString = IOUtils.toString(is, StandardCharsets.UTF_8);
            assertEquals("value1", binary2asString);
        }
        try (InputStream is = binary2AsBinary[1].getStream()) {
            String binary2asString = IOUtils.toString(is, StandardCharsets.UTF_8);
            assertEquals("value2", binary2asString);
        }
        Binary[] binary2aAsBinary = vm.get("binary2a", new Binary[] {defaultBinary1, defaultBinary2});
        assertEquals(2, binary2aAsBinary.length);
        try (InputStream is = binary2aAsBinary[0].getStream()) {
            String binary2asString = IOUtils.toString(is, StandardCharsets.UTF_8);
            assertEquals("default1", binary2asString);
        }
        try (InputStream is = binary2aAsBinary[1].getStream()) {
            String binary2asString = IOUtils.toString(is, StandardCharsets.UTF_8);
            assertEquals("default2", binary2asString);
        }

        assertEquals(Boolean.FALSE, vm.get("boolean1", Boolean.TRUE));
        assertEquals(Boolean.TRUE, vm.get("boolean1a", Boolean.TRUE));
        assertArrayEquals(new Boolean[] {Boolean.FALSE, Boolean.TRUE}, vm.get("boolean2", new Boolean[] {true, true}));
        assertArrayEquals(new Boolean[] {Boolean.TRUE, Boolean.TRUE}, vm.get("boolean2a", new Boolean[] {true, true}));

        assertEquals(Long.valueOf(1), vm.get("long1", 2L));
        assertEquals(Long.valueOf(2), vm.get("long1a", 2L));
        assertArrayEquals(new Long[] {1L, 2L}, vm.get("long2", new Long[] {1L, 1L}));
        assertArrayEquals(new Long[] {1L, 1L}, vm.get("long2a", new Long[] {1L, 1L}));

        // other types of numbers
        assertEquals(Short.valueOf((short)1), vm.get("long1",(short)2));
        assertEquals(Short.valueOf((short)2), vm.get("long1a",(short)2));
        assertArrayEquals(new Short[] {1, 2}, vm.get("long2", new Short[] {1, 1}));
        assertArrayEquals(new Short[] {1, 1}, vm.get("long2a", new Short[] {1, 1}));

        assertEquals(Integer.valueOf(1), vm.get("long1", (int)2));
        assertEquals(Integer.valueOf(2), vm.get("long1a", (int)2));
        assertArrayEquals(new Integer[] {1, 2}, vm.get("long2", new Integer[] {1, 1}));
        assertArrayEquals(new Integer[] {1, 1}, vm.get("long2a", new Integer[] {1, 1}));

        assertEquals(Byte.valueOf((byte)1), vm.get("long1", (byte)2));
        assertEquals(Byte.valueOf((byte)2), vm.get("long1a", (byte)2));
        assertArrayEquals(new Byte[] {1, 2}, vm.get("long2", new Byte[] {1, 1}));
        assertArrayEquals(new Byte[] {1, 1}, vm.get("long2a", new Byte[] {1, 1}));

        assertEquals(Double.valueOf(1.1), vm.get("double1", 2.2));
        assertEquals(Double.valueOf(2.2), vm.get("double1a", 2.2));
        assertArrayEquals(new Double[] {1.1, 2.2}, vm.get("double2", new Double[] {1.1, 1.1}));
        assertArrayEquals(new Double[] {1.1, 1.1}, vm.get("double2a", new Double[] {1.1, 1.1}));

        // other types of numbers
        assertEquals(Float.valueOf((float)1.1), vm.get("double1", (float)2.2));
        assertEquals(Float.valueOf((float)2.2), vm.get("double1a", (float)2.2));
        assertArrayEquals(new Float[] {(float)1.1, (float)2.2}, vm.get("double2", new Float[] {(float)1.1, (float)1.1}));
        assertArrayEquals(new Float[] {(float)1.1, (float)1.1}, vm.get("double2a", new Float[] {(float)1.1, (float)1.1}));

        assertEquals(new BigDecimal(1), vm.get("decimal1", new BigDecimal(2)));
        assertEquals(new BigDecimal(2), vm.get("decimal1a", new BigDecimal(2)));
        assertArrayEquals(new BigDecimal[] {new BigDecimal(1), new BigDecimal(2)}, vm.get("decimal2", new BigDecimal[] { new BigDecimal(1), new BigDecimal(1)}));
        assertArrayEquals(new BigDecimal[] {new BigDecimal(1), new BigDecimal(1)}, vm.get("decimal2a", new BigDecimal[] { new BigDecimal(1), new BigDecimal(1)}));

        Calendar date1 = vm.get("date1", NOW);
        assertEquals(ISO8601.format(NOW), ISO8601.format(date1));
        Calendar date1a = vm.get("date1a", NOW);
        assertEquals(ISO8601.format(NOW), ISO8601.format(date1a));
        Calendar[] date2 = vm.get("date2", new Calendar[] {NOW, NOW});
        assertEquals(2, date2.length);
        assertEquals(ISO8601.format(NOW), ISO8601.format(date2[0]));
        assertEquals(ISO8601.format(NOW), ISO8601.format(date2[1]));
        Calendar[] date2a = vm.get("date2a", new Calendar[] {NOW, NOW});
        assertEquals(2, date2a.length);
        assertEquals(ISO8601.format(NOW), ISO8601.format(date2a[0]));
        assertEquals(ISO8601.format(NOW), ISO8601.format(date2a[1]));

        // other types of date
        assertEquals(NOW.getTime(), vm.get("date1", NOW.getTime()));
        assertEquals(NOW.getTime(), vm.get("date1a", NOW.getTime()));
        assertArrayEquals(new Date[] {NOW.getTime(), NOW.getTime()}, vm.get("date2", new Date[] {NOW.getTime(), NOW.getTime()}));
        assertArrayEquals(new Date[] {NOW.getTime(), NOW.getTime()}, vm.get("date2a", new Date[] {NOW.getTime(), NOW.getTime()}));

        assertValueEquals(valueFactory.createValue("name1", PropertyType.NAME), vm.get("name1", valueFactory.createValue("name2", PropertyType.NAME)));
        assertValueEquals(valueFactory.createValue("name2", PropertyType.NAME), vm.get("name1a", valueFactory.createValue("name2", PropertyType.NAME)));
        assertValueArrayEquals(new Value[] {valueFactory.createValue("name1", PropertyType.NAME), valueFactory.createValue("name2", PropertyType.NAME)}, vm.get("name2", new Value[] {valueFactory.createValue("name1", PropertyType.NAME), valueFactory.createValue("name3", PropertyType.NAME)}));
        assertValueArrayEquals(new Value[] {valueFactory.createValue("name1", PropertyType.NAME), valueFactory.createValue("name3", PropertyType.NAME)}, vm.get("name2a", new Value[] {valueFactory.createValue("name1", PropertyType.NAME), valueFactory.createValue("name3", PropertyType.NAME)}));

        assertValueEquals(valueFactory.createValue("/content", PropertyType.PATH), vm.get("path1", valueFactory.createValue("/content2", PropertyType.PATH)));
        assertValueEquals(valueFactory.createValue("/content2", PropertyType.PATH), vm.get("path1a", valueFactory.createValue("/content2", PropertyType.PATH)));
        assertValueArrayEquals(new Value[] {valueFactory.createValue("/content", PropertyType.PATH), valueFactory.createValue("/home", PropertyType.PATH)}, vm.get("path2", new Value[] {valueFactory.createValue("/content2", PropertyType.PATH), valueFactory.createValue("/home2", PropertyType.PATH)}));
        assertValueArrayEquals(new Value[] {valueFactory.createValue("/content2", PropertyType.PATH), valueFactory.createValue("/home2", PropertyType.PATH)}, vm.get("path2a", new Value[] {valueFactory.createValue("/content2", PropertyType.PATH), valueFactory.createValue("/home2", PropertyType.PATH)}));

        assertValueEquals(valueFactory.createValue(uuid, PropertyType.REFERENCE), vm.get("reference1", valueFactory.createValue(uuid2, PropertyType.REFERENCE)));
        assertValueEquals(valueFactory.createValue(uuid2, PropertyType.REFERENCE), vm.get("reference1a", valueFactory.createValue(uuid2, PropertyType.REFERENCE)));
        assertValueArrayEquals(new Value[] {valueFactory.createValue(uuid, PropertyType.REFERENCE), valueFactory.createValue(uuid, PropertyType.REFERENCE)}, vm.get("reference2", new Value[] {valueFactory.createValue(uuid2, PropertyType.REFERENCE), valueFactory.createValue(uuid2, PropertyType.REFERENCE)}));
        assertValueArrayEquals(new Value[] {valueFactory.createValue(uuid2, PropertyType.REFERENCE), valueFactory.createValue(uuid2, PropertyType.REFERENCE)}, vm.get("reference2a", new Value[] {valueFactory.createValue(uuid2, PropertyType.REFERENCE), valueFactory.createValue(uuid2, PropertyType.REFERENCE)}));

        assertValueEquals(valueFactory.createValue(uuid, PropertyType.WEAKREFERENCE), vm.get("weakreference1", valueFactory.createValue(uuid2, PropertyType.WEAKREFERENCE)));
        assertValueEquals(valueFactory.createValue(uuid2, PropertyType.WEAKREFERENCE), vm.get("weakreference1a", valueFactory.createValue(uuid2, PropertyType.WEAKREFERENCE)));
        assertValueArrayEquals(new Value[] {valueFactory.createValue(uuid, PropertyType.WEAKREFERENCE), valueFactory.createValue(uuid, PropertyType.WEAKREFERENCE)}, vm.get("weakreference2", new Value[] {valueFactory.createValue(uuid2, PropertyType.WEAKREFERENCE), valueFactory.createValue(uuid2, PropertyType.WEAKREFERENCE)}));
        assertValueArrayEquals(new Value[] {valueFactory.createValue(uuid2, PropertyType.WEAKREFERENCE), valueFactory.createValue(uuid2, PropertyType.WEAKREFERENCE)}, vm.get("weakreference2a", new Value[] {valueFactory.createValue(uuid2, PropertyType.WEAKREFERENCE), valueFactory.createValue(uuid2, PropertyType.WEAKREFERENCE)}));

        assertValueEquals(valueFactory.createValue("http://localhost:8080/content", PropertyType.URI), vm.get("uri1", valueFactory.createValue("http://localhost:8080/content2", PropertyType.URI)));
        assertValueEquals(valueFactory.createValue("http://localhost:8080/content2", PropertyType.URI), vm.get("uri1a", valueFactory.createValue("http://localhost:8080/content2", PropertyType.URI)));
        assertValueArrayEquals(new Value[] {valueFactory.createValue("http://localhost:8080/content", PropertyType.URI), valueFactory.createValue("http://localhost:8080/home", PropertyType.URI)}, vm.get("uri2",new Value[] {valueFactory.createValue("http://localhost:8080/content2", PropertyType.URI), valueFactory.createValue("http://localhost:8080/home2", PropertyType.URI)}));
        assertValueArrayEquals(new Value[] {valueFactory.createValue("http://localhost:8080/content2", PropertyType.URI), valueFactory.createValue("http://localhost:8080/home2", PropertyType.URI)}, vm.get("uri2a",new Value[] {valueFactory.createValue("http://localhost:8080/content2", PropertyType.URI), valueFactory.createValue("http://localhost:8080/home2", PropertyType.URI)}));

        assertEquals("value1", vm.get("undefined1", "default1"));
        assertEquals("default1", vm.get("undefined1a", "default1"));
        assertArrayEquals(new String[] {"value1", "value2"}, vm.get("undefined2", new String[] {"default1", "default2"}));
        assertArrayEquals(new String[] {"default1", "default2"}, vm.get("undefined2a", new String[] {"default1", "default2"}));
    }

    @Test
    public void testContainsKey() throws LoginException, RepositoryException {
        ValueMap vm = getValueMap(user1);
        assertTrue(vm.containsKey("key1"));
        assertFalse(vm.containsKey("not_a_key1"));

        ValueMap vm2 = getValueMap(group1);
        assertTrue(vm2.containsKey("key1"));
        assertFalse(vm2.containsKey("not_a_key1"));
    }

    @Test
    public void testContainsValue() throws LoginException, RepositoryException {
        ValueMap vm = getValueMap(user1);
        assertTrue(vm.containsValue("value1"));
        assertFalse(vm.containsValue("not_a_value1"));

        ValueMap vm2 = getValueMap(group1);
        assertTrue(vm2.containsValue("value1"));
        assertFalse(vm2.containsValue("not_a_value1"));
    }

    @Test
    public void testEntrySet() throws LoginException, RepositoryException {
        ValueMap vm = getValueMap(user1);
        Set<Entry<String, Object>> entrySet = vm.entrySet();
        assertNotNull(entrySet);

        ValueMap vm2 = getValueMap(group1);
        Set<Entry<String, Object>> entrySet2 = vm2.entrySet();
        assertNotNull(entrySet2);
    }

    @Test
    public void testGetObject() throws LoginException, RepositoryException, IOException {
        ValueMap vm = getValueMap(user1);
        Object vmValue1 = vm.get("key1");
        assertEquals("value1", vmValue1);
        Object vmValue2 = vm.get("not_a_key1");
        assertNull(vmValue2);
        //read again to cover the cached state
        Object vmValue3 = vm.get("key1");
        assertEquals("value1", vmValue3);

        ValueMap vm2 = getValueMap(group1);
        Object vm2Value1 = vm2.get("key1");
        assertEquals("value1", vm2Value1);
        Object vm2Value2 = vm2.get("not_a_key1");
        assertNull(vm2Value2);
        //read again to cover the cached state
        Object vm2Value3 = vm2.get("key1");
        assertEquals("value1", vm2Value3);


        assertEquals("value1", vm.get("string1"));
        assertArrayEquals(new Object[] {"value1", "value2"}, (Object[])vm.get("string2"));

        Object binary1 = vm.get("binary1");
        assertTrue(binary1 instanceof InputStream);
        try (InputStream is = (InputStream)binary1) {
            String binary1asString = IOUtils.toString(is, StandardCharsets.UTF_8);
            assertEquals("value1", binary1asString);
        }
        Object binary2 = vm.get("binary2");
        assertTrue(binary2.getClass().isArray());
        assertEquals(2, Array.getLength(binary2));
        try (InputStream is = (InputStream)Array.get(binary2, 0)) {
            String binary2asString = IOUtils.toString(is, StandardCharsets.UTF_8);
            assertEquals("value1", binary2asString);
        }
        try (InputStream is = (InputStream)Array.get(binary2, 1)) {
            String binary2asString = IOUtils.toString(is, StandardCharsets.UTF_8);
            assertEquals("value2", binary2asString);
        }

        assertEquals(Boolean.FALSE, vm.get("boolean1"));
        assertArrayEquals(new Object[] {Boolean.FALSE, Boolean.TRUE}, (Object[])vm.get("boolean2"));

        assertEquals(Long.valueOf(1), vm.get("long1"));
        assertArrayEquals(new Object[] {1L, 2L}, (Object[])vm.get("long2"));

        assertEquals(Double.valueOf(1.1), vm.get("double1"));
        assertArrayEquals(new Object[] {1.1, 2.2}, (Object[])vm.get("double2"));

        assertEquals(new BigDecimal(1), vm.get("decimal1"));
        assertArrayEquals(new Object[] {new BigDecimal(1), new BigDecimal(2)}, (Object[])vm.get("decimal2"));

        Object date1 = vm.get("date1");
        assertTrue(date1 instanceof Calendar);
        assertEquals(ISO8601.format(NOW), ISO8601.format((Calendar)date1));
        Object date2 = vm.get("date2");
        assertTrue(date2.getClass().isArray());
        assertEquals(2, Array.getLength(date2));
        assertEquals(ISO8601.format(NOW), ISO8601.format((Calendar)Array.get(date2, 0)));
        assertEquals(ISO8601.format(NOW), ISO8601.format((Calendar)Array.get(date2, 1)));

        assertEquals("name1", vm.get("name1"));
        assertArrayEquals(new Object[] {"name1", "name2"}, (Object[])vm.get("name2"));

        assertEquals("/content", vm.get("path1"));
        assertArrayEquals(new Object[] {"/content", "/home"}, (Object[])vm.get("path2"));

        assertEquals(uuid, vm.get("reference1"));
        assertArrayEquals(new Object[] {uuid, uuid}, (Object[])vm.get("reference2"));

        assertEquals(uuid, vm.get("weakreference1"));
        assertArrayEquals(new Object[] {uuid, uuid}, (Object[])vm.get("weakreference2"));

        assertEquals("http://localhost:8080/content", vm.get("uri1"));
        assertArrayEquals(new Object[] {"http://localhost:8080/content", "http://localhost:8080/home"}, (Object[])vm.get("uri2"));

        assertEquals("value1", vm.get("undefined1"));
        assertArrayEquals(new Object[] {"value1", "value2"}, (Object[])vm.get("undefined2"));
    }

    @Test
    public void testKeySet() throws LoginException, RepositoryException {
        ValueMap vm = getValueMap(user1);
        Set<String> keySet = vm.keySet();
        assertNotNull(keySet);

        ValueMap vm2 = getValueMap(group1);
        Set<String> keySet2 = vm2.keySet();
        assertNotNull(keySet2);
    }

    @Test
    public abstract void testSize() throws LoginException, RepositoryException;

    @Test
    public void testIsEmpty() throws LoginException, RepositoryException {
        ValueMap vm = getValueMap(user1);
        assertFalse(vm.isEmpty());

        ValueMap vm2 = getValueMap(group1);
        assertFalse(vm2.isEmpty());
    }

    @Test
    public void testValues() throws LoginException, RepositoryException {
        ValueMap vm = getValueMap(user1);
        Collection<Object> values = vm.values();
        assertNotNull(values);

        ValueMap vm2 = getValueMap(group1);
        Collection<Object> values2 = vm2.values();
        assertNotNull(values2);
    }

    @Test
    public void testToString() throws LoginException, RepositoryException {
        ValueMap vm = getValueMap(user1);
        String string = vm.toString();
        assertNotNull(string);

        ValueMap vm2 = getValueMap(group1);
        String string2 = vm2.toString();
        assertNotNull(string2);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testRemoveFromUser() throws LoginException, RepositoryException {
        ValueMap vm = getValueMap(user1);
        vm.remove("key1");
    }
    @Test(expected = UnsupportedOperationException.class)
    public void testRemoveFromGroup() throws LoginException, RepositoryException {
        ValueMap vm2 = getValueMap(group1);
        vm2.remove("key1");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testClearFromUser() throws LoginException, RepositoryException {
        ValueMap vm = getValueMap(user1);
        vm.clear();
    }
    @Test(expected = UnsupportedOperationException.class)
    public void testClearFromGroup() throws LoginException, RepositoryException {
        ValueMap vm2 = getValueMap(group1);
        vm2.clear();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testPutFromUser() throws LoginException, RepositoryException {
        ValueMap vm = getValueMap(user1);
        vm.put("another", "value");
    }
    @Test(expected = UnsupportedOperationException.class)
    public void testPutFromGroup() throws LoginException, RepositoryException {
        ValueMap vm2 = getValueMap(group1);
        vm2.put("another", "value");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testPutAllFromUser() throws LoginException, RepositoryException {
        ValueMap vm = getValueMap(user1);
        vm.putAll(Collections.singletonMap("another", "value"));
    }
    @Test(expected = UnsupportedOperationException.class)
    public void testPutAllFromGroup() throws LoginException, RepositoryException {
        ValueMap vm2 = getValueMap(group1);
        vm2.putAll(Collections.singletonMap("another", "value"));
    }

    protected ValueMap getValueMap(Authorizable a) throws LoginException, RepositoryException {
        try (ResourceResolver resourceResolver = resourceResolverFactory.getResourceResolver(Collections.singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, adminSession))) {
            Resource resource;
            if (a.isGroup()) {
                resource = resourceResolver.resolve(String.format("%s%s", userManagerPaths.getGroupPrefix(), a.getID()));
            } else {
                resource = resourceResolver.resolve(String.format("%s%s", userManagerPaths.getUserPrefix(), a.getID()));
            }
            assertNotNull(resource);
            ValueMap vm = resource.adaptTo(ValueMap.class);
            assertNotNull(vm);
            return vm;
        }
    }

    protected void assertValueEquals(Value expected, Value actual) throws RepositoryException {
        assertEquals(expected.getType(), actual.getType());
        assertEquals(expected.getString(), actual.getString());
    }

    protected void assertValueArrayEquals(Value[] expected, Value[] actual) throws RepositoryException {
        assertEquals(expected.length, actual.length);
        for (int i=0; i < expected.length; i++) {
            assertEquals(String.format("item %d was not equal", i), expected[i].getString(), actual[i].getString());
        }
    }

}
