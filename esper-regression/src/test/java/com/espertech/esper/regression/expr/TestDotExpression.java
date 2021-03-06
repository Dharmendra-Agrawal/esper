/*
 ***************************************************************************************
 *  Copyright (C) 2006 EsperTech, Inc. All rights reserved.                            *
 *  http://www.espertech.com/esper                                                     *
 *  http://www.espertech.com                                                           *
 *  ---------------------------------------------------------------------------------- *
 *  The software in this package is published under the terms of the GPL license       *
 *  a copy of which has been included with this distribution in the license.txt file.  *
 ***************************************************************************************
 */
package com.espertech.esper.regression.expr;

import com.espertech.esper.client.*;
import com.espertech.esper.client.scopetest.EPAssertionUtil;
import com.espertech.esper.client.scopetest.SupportUpdateListener;
import com.espertech.esper.client.soda.EPStatementObjectModel;
import com.espertech.esper.metrics.instrumentation.InstrumentationHelper;
import com.espertech.esper.supportregression.bean.*;
import com.espertech.esper.supportregression.client.SupportConfigFactory;
import com.espertech.esper.supportregression.events.SampleEnumInEventsPackage;
import junit.framework.TestCase;

import java.util.Collections;
import java.util.Map;

public class TestDotExpression extends TestCase
{
	private EPServiceProvider epService;
	private SupportUpdateListener listener;

	protected void setUp()
	{
	    epService = EPServiceProviderManager.getDefaultProvider(SupportConfigFactory.getConfiguration());
	    epService.initialize();
        if (InstrumentationHelper.ENABLED) { InstrumentationHelper.startTest(epService, this.getClass(), getName());}
        listener = new SupportUpdateListener();
	}

    protected void tearDown() throws Exception {
        if (InstrumentationHelper.ENABLED) { InstrumentationHelper.endTest();}
        listener = null;
    }

    public void testDotObjectEquals() {
        epService.getEPAdministrator().getConfiguration().addEventType(SupportBean.class);
        epService.getEPAdministrator().createEPL("select sb.equals(maxBy(intPrimitive)) as c0 from SupportBean as sb").addListener(listener);

        sendAssertDotObjectEquals(10, true);
        sendAssertDotObjectEquals(9, false);
        sendAssertDotObjectEquals(11, true);
        sendAssertDotObjectEquals(8, false);
        sendAssertDotObjectEquals(11, false);
        sendAssertDotObjectEquals(12, true);
    }

    private void sendAssertDotObjectEquals(int intPrimitive, boolean expected) {
        epService.getEPRuntime().sendEvent(new SupportBean(null, intPrimitive));
        EPAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), "c0".split(","), new Object[] {expected});
    }

    public void testDotExpressionEnumValue() {
        epService.getEPAdministrator().getConfiguration().addImport(SupportEnumTwo.class);
        epService.getEPAdministrator().getConfiguration().addEventType(SupportBean.class);

        String[] fields = "c0,c1,c2,c3".split(",");
        epService.getEPAdministrator().createEPL("select " +
                "intPrimitive = SupportEnumTwo.ENUM_VALUE_1.getAssociatedValue() as c0," +
                "SupportEnumTwo.ENUM_VALUE_2.checkAssociatedValue(intPrimitive) as c1, " +
                "SupportEnumTwo.ENUM_VALUE_3.getNested().getValue() as c2," +
                "SupportEnumTwo.ENUM_VALUE_2.checkEventBeanPropInt(sb, 'intPrimitive') as c3, " +
                "SupportEnumTwo.ENUM_VALUE_2.checkEventBeanPropInt(*, 'intPrimitive') as c4 " +
                "from SupportBean as sb").addListener(listener);

        epService.getEPRuntime().sendEvent(new SupportBean("E1", 100));
        EPAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fields, new Object[] {true, false, 300, false});

        epService.getEPRuntime().sendEvent(new SupportBean("E1", 200));
        EPAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fields, new Object[] {false, true, 300, true});

        // test "events" reserved keyword in package name
        epService.getEPAdministrator().createEPL("select " + SampleEnumInEventsPackage.class.getName() + ".A from SupportBean");
    }

    public void testMapIndexPropertyRooted() {
        epService.getEPAdministrator().getConfiguration().addEventType(MyTypeErasure.class);
        EPStatement stmt = epService.getEPAdministrator().createEPL("select " +
                "innerTypes('key1') as c0,\n" +
                "innerTypes(key) as c1,\n" +
                "innerTypes('key1').ids[1] as c2,\n" +
                "innerTypes(key).getIds(subkey) as c3,\n" +
                "innerTypesArray[1].ids[1] as c4,\n" +
                "innerTypesArray(subkey).getIds(subkey) as c5,\n" +
                "innerTypesArray(subkey).getIds(s0, 'xyz') as c6,\n" +
                "innerTypesArray(subkey).getIds(*, 'xyz') as c7\n" +
                "from MyTypeErasure as s0");
        stmt.addListener(listener);
        assertEquals(InnerType.class, stmt.getEventType().getPropertyType("c0"));
        assertEquals(InnerType.class, stmt.getEventType().getPropertyType("c1"));
        assertEquals(int.class, stmt.getEventType().getPropertyType("c2"));
        assertEquals(int.class, stmt.getEventType().getPropertyType("c3"));
        
        MyTypeErasure event = new MyTypeErasure("key1", 2, Collections.singletonMap("key1", new InnerType(new int[] {20, 30, 40})), new InnerType[] {new InnerType(new int[] {2, 3}), new InnerType(new int[] {4, 5}), new InnerType(new int[] {6, 7, 8})});
        epService.getEPRuntime().sendEvent(event);
        EPAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), "c0,c1,c2,c3,c4,c5,c6,c7".split(","), new Object[] {event.getInnerTypes().get("key1"), event.getInnerTypes().get("key1"), 30, 40, 5, 8, 999999, 999999});
    }

    public void testInvalid() {
        epService.getEPAdministrator().getConfiguration().addEventType("SupportBean", SupportBean.class);
        epService.getEPAdministrator().getConfiguration().addEventType("SupportChainTop", SupportChainTop.class);

        tryInvalid("select abc.noSuchMethod() from SupportBean abc",
                "Error starting statement: Failed to validate select-clause expression 'abc.noSuchMethod()': Failed to solve 'noSuchMethod' to either an date-time or enumeration method, an event property or a method on the event underlying object: Failed to resolve method 'noSuchMethod': Could not find enumeration method, date-time method or instance method named 'noSuchMethod' in class '" + SupportBean.class.getName() + "' taking no parameters [select abc.noSuchMethod() from SupportBean abc]");
        tryInvalid("select abc.getChildOne(\"abc\", 10).noSuchMethod() from SupportChainTop abc",
                "Error starting statement: Failed to validate select-clause expression 'abc.getChildOne(\"abc\",10).noSuchMethod()': Failed to solve 'getChildOne' to either an date-time or enumeration method, an event property or a method on the event underlying object: Failed to resolve method 'noSuchMethod': Could not find enumeration method, date-time method or instance method named 'noSuchMethod' in class '" + SupportChainChildOne.class.getName() + "' taking no parameters [select abc.getChildOne(\"abc\", 10).noSuchMethod() from SupportChainTop abc]");
    }

    public void testNestedPropertyInstanceExpr() {
        epService.getEPAdministrator().getConfiguration().addEventType("LevelZero", LevelZero.class);
        epService.getEPAdministrator().createEPL("select " +
                "levelOne.getCustomLevelOne(10) as val0, " +
                "levelOne.levelTwo.getCustomLevelTwo(20) as val1, " +
                "levelOne.levelTwo.levelThree.getCustomLevelThree(30) as val2 " +
                "from LevelZero").addListener(listener);
        
        epService.getEPRuntime().sendEvent(new LevelZero(new LevelOne(new LevelTwo(new LevelThree()))));
        EPAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), "val0,val1,val2".split(","), new Object[]{"level1:10", "level2:20", "level3:30"});

        // ESPER-772
        epService.getEPAdministrator().getConfiguration().addEventType(Node.class);
        epService.getEPAdministrator().getConfiguration().addEventType(NodeData.class);

        epService.getEPAdministrator().createEPL("create window NodeWindow#unique(id) as Node");
        epService.getEPAdministrator().createEPL("insert into NodeWindow select * from Node");

        epService.getEPAdministrator().createEPL("create window NodeDataWindow#unique(nodeId) as NodeData");
        epService.getEPAdministrator().createEPL("insert into NodeDataWindow select * from NodeData");

        epService.getEPAdministrator().createEPL("create schema NodeWithData(node Node, data NodeData)");
        epService.getEPAdministrator().createEPL("create window NodeWithDataWindow#unique(node.id) as NodeWithData");
        epService.getEPAdministrator().createEPL("insert into NodeWithDataWindow " +
                "select node, data from NodeWindow node join NodeDataWindow as data on node.id = data.nodeId");

        EPStatement stmt = epService.getEPAdministrator().createEPL("select node.id, data.nodeId, data.value, node.compute(data) from NodeWithDataWindow");
        stmt.addListener(listener);

        epService.getEPRuntime().sendEvent(new Node("1"));
        epService.getEPRuntime().sendEvent(new Node("2"));
        epService.getEPRuntime().sendEvent(new NodeData("1", "xxx"));
    }

    public void testChainedUnparameterized() {
        epService.getEPAdministrator().getConfiguration().addEventType("SupportBeanComplexProps", SupportBeanComplexProps.class);

        String epl = "select " +
                "nested.getNestedValue(), " +
                "nested.getNestedNested().getNestedNestedValue() " +
                "from SupportBeanComplexProps";
        EPStatement stmt = epService.getEPAdministrator().createEPL(epl);
        stmt.addListener(listener);

        SupportBeanComplexProps bean = SupportBeanComplexProps.makeDefaultBean();
        Object[][] rows = new Object[][] {
                {"nested.getNestedValue()", String.class}
                };
        for (int i = 0; i < rows.length; i++) {
            EventPropertyDescriptor prop = stmt.getEventType().getPropertyDescriptors()[i];
            assertEquals(rows[i][0], prop.getPropertyName());
            assertEquals(rows[i][1], prop.getPropertyType());
        }

        epService.getEPRuntime().sendEvent(bean);
        EPAssertionUtil.assertProps(listener.assertOneGetNew(), "nested.getNestedValue()".split(","), new Object[]{bean.getNested().getNestedValue()});
    }

    public void testChainedParameterized() {
        epService.getEPAdministrator().getConfiguration().addEventType("SupportChainTop", SupportChainTop.class);

        String subexpr="top.getChildOne(\"abc\",10).getChildTwo(\"append\")";
        String epl = "select " +
                subexpr +
                " from SupportChainTop as top";
        EPStatement stmt = epService.getEPAdministrator().createEPL(epl);
        stmt.addListener(listener);

        runAssertionChainedParam(stmt, subexpr);

        listener.reset();
        stmt.destroy();
        EPStatementObjectModel model = epService.getEPAdministrator().compileEPL(epl);
        assertEquals(epl, model.toEPL());
        stmt = epService.getEPAdministrator().create(model);
        stmt.addListener(listener);

        runAssertionChainedParam(stmt, subexpr);
    }

    public void testArrayPropertySizeAndGet() {
        epService.getEPAdministrator().getConfiguration().addEventType("SupportBeanComplexProps", SupportBeanComplexProps.class);

        String epl = "select " +
                "(arrayProperty).size() as size, " +
                "(arrayProperty).get(0) as get0, " +
                "(arrayProperty).get(1) as get1, " +
                "(arrayProperty).get(2) as get2, " +
                "(arrayProperty).get(3) as get3 " +
                "from SupportBeanComplexProps";
        EPStatement stmt = epService.getEPAdministrator().createEPL(epl);
        stmt.addListener(listener);

        SupportBeanComplexProps bean = SupportBeanComplexProps.makeDefaultBean();
        Object[][] rows = new Object[][] {
                {"size", Integer.class},
                {"get0", int.class},
                {"get1", int.class},
                {"get2", int.class},
                {"get3", int.class}
                };
        for (int i = 0; i < rows.length; i++) {
            EventPropertyDescriptor prop = stmt.getEventType().getPropertyDescriptors()[i];
            assertEquals("failed for " + rows[i][0], rows[i][0], prop.getPropertyName());
            assertEquals("failed for " + rows[i][0], rows[i][1], prop.getPropertyType());
        }

        epService.getEPRuntime().sendEvent(bean);
        EPAssertionUtil.assertProps(listener.assertOneGetNew(), "size,get0,get1,get2,get3".split(","),
                new Object[]{bean.getArrayProperty().length, bean.getArrayProperty()[0], bean.getArrayProperty()[1], bean.getArrayProperty()[2], null});
    }
    
    public void testArrayPropertySizeAndGetChained() {
        epService.getEPAdministrator().getConfiguration().addEventType("SupportBeanCombinedProps", SupportBeanCombinedProps.class);

        String epl = "select " +
                "(abc).getArray().size() as size, " +
                "(abc).getArray().get(0).getNestLevOneVal() as get0 " +
                "from SupportBeanCombinedProps as abc";
        EPStatement stmt = epService.getEPAdministrator().createEPL(epl);
        stmt.addListener(listener);

        SupportBeanCombinedProps bean = SupportBeanCombinedProps.makeDefaultBean();
        Object[][] rows = new Object[][] {
                {"size", Integer.class},
                {"get0", String.class},
                };
        for (int i = 0; i < rows.length; i++) {
            EventPropertyDescriptor prop = stmt.getEventType().getPropertyDescriptors()[i];
            assertEquals(rows[i][0], prop.getPropertyName());
            assertEquals(rows[i][1], prop.getPropertyType());
        }

        epService.getEPRuntime().sendEvent(bean);
        EPAssertionUtil.assertProps(listener.assertOneGetNew(), "size,get0".split(","),
                new Object[]{bean.getArray().length, bean.getArray()[0].getNestLevOneVal()});
    }

    private void runAssertionChainedParam(EPStatement stmt, String subexpr) {

        Object[][] rows = new Object[][] {
                {subexpr, SupportChainChildTwo.class}
                };
        for (int i = 0; i < rows.length; i++) {
            EventPropertyDescriptor prop = stmt.getEventType().getPropertyDescriptors()[i];
            assertEquals(rows[i][0], prop.getPropertyName());
            assertEquals(rows[i][1], prop.getPropertyType());
        }

        epService.getEPRuntime().sendEvent(new SupportChainTop());
        Object result = listener.assertOneGetNewAndReset().get(subexpr);
        assertEquals("abcappend", ((SupportChainChildTwo)result).getText());
    }

    private void tryInvalid(String epl, String message)
    {
        try {
            epService.getEPAdministrator().createEPL(epl);
            fail();
        }
        catch (EPStatementException ex) {
            assertEquals(message, ex.getMessage());
        }
    }

    public static class LevelZero {
        private LevelOne levelOne;

        private LevelZero(LevelOne levelOne) {
            this.levelOne = levelOne;
        }

        public LevelOne getLevelOne() {
            return levelOne;
        }
    }

    public static class LevelOne {
        private LevelTwo levelTwo;

        public LevelOne(LevelTwo levelTwo) {
            this.levelTwo = levelTwo;
        }

        public LevelTwo getLevelTwo() {
            return levelTwo;
        }

        public String getCustomLevelOne(int val) {
            return "level1:" + val;
        }
    }

    public static class LevelTwo {
        private LevelThree levelThree;

        public LevelTwo(LevelThree levelThree) {
            this.levelThree = levelThree;
        }

        public LevelThree getLevelThree() {
            return levelThree;
        }

        public String getCustomLevelTwo(int val) {
            return "level2:" + val;
        }
    }

    public static class LevelThree {
        public String getCustomLevelThree(int val) {
            return "level3:" + val;
        }
    }

    public static class MyTypeErasure {

        private String key;
        private int subkey;
        private Map<String, InnerType> innerTypes;
        private InnerType[] innerTypesArray;

        public MyTypeErasure(String key, int subkey, Map<String, InnerType> innerTypes, InnerType[] innerTypesArray) {
            this.key = key;
            this.subkey = subkey;
            this.innerTypes = innerTypes;
            this.innerTypesArray = innerTypesArray;
        }

        public Map<String, InnerType> getInnerTypes() {
            return innerTypes;
        }

        public String getKey() {
            return key;
        }

        public int getSubkey() {
            return subkey;
        }

        public InnerType[] getInnerTypesArray() {
            return innerTypesArray;
        }
    }

    public static class InnerType {

        private final int[] ids;

        public InnerType(int[] ids) {
            this.ids = ids;
        }

        public int[] getIds() {
            return ids;
        }

        public int getIds(int subkey) {
            return ids[subkey];
        }

        public int getIds(EventBean event, String name) {
            return 999999;
        }
    }

    public static class Node {
        public String id;

        public Node(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public String compute(Object data) {
            if (data == null) {
                return null;
            }
            NodeData nodeData = (NodeData) ((EventBean) data).getUnderlying();
            return id + nodeData.getValue();
        }
    }

    public static class NodeData {

        public String nodeId;
        public String value;

        public NodeData(String nodeId, String value) {
            this.nodeId = nodeId;
            this.value = value;
        }

        public String getNodeId() {
            return nodeId;
        }

        public String getValue() {
            return value;
        }
    }
}
