package com.github.foeser.teamcity.elastictimeout;

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.github.foeser.teamcity.elastictimeout.ElasticTimeoutFailureCondition.PARAM_BUILD_COUNT;
import static com.github.foeser.teamcity.elastictimeout.ElasticTimeoutFailureCondition.PARAM_EXCEED_VALUE;

public class ParameterValidationTests extends BaseTestCase {
    private Mockery context;
    private ElasticTimeoutFailureCondition failureCondition;

    @Override
    @BeforeMethod
    public void setUp() throws Exception {
        super.setUp();
        context = new Mockery();
        PluginDescriptor mockPluginDescriptor = context.mock(PluginDescriptor.class);
        context.checking(new Expectations() {
            {
                oneOf(mockPluginDescriptor).getPluginResourcesPath("ElasticTimeoutFailureConditionSettings.jsp"); will (returnValue(""));
            }
        });
        failureCondition = new ElasticTimeoutFailureCondition(mockPluginDescriptor);
    }

    private ArrayList<InvalidProperty> setupParamTest(String build_count, String exceed_value) {
        Map<String, String> faultyParameters = Map.ofEntries(
                new AbstractMap.SimpleEntry(PARAM_BUILD_COUNT, build_count),
                new AbstractMap.SimpleEntry(PARAM_EXCEED_VALUE, exceed_value)
        );
        return new ArrayList<>(failureCondition.getParametersProcessor().process(faultyParameters));
    }

    @Test
    void testParamBuildCountUserInput() {
        List<InvalidProperty> invalidProperties = setupParamTest(" ", "25");
        assertEquals(1, invalidProperties.size());
        assertEquals("You need to define a build count.", invalidProperties.get(0).getInvalidReason());

        invalidProperties = setupParamTest("", "25");
        assertEquals(1, invalidProperties.size());
        assertEquals("You need to define a build count.", invalidProperties.get(0).getInvalidReason());

        invalidProperties = setupParamTest("-34", "25");
        assertEquals(1, invalidProperties.size());
        assertEquals("Only positive numbers are allowed for the build count.", invalidProperties.get(0).getInvalidReason());

        invalidProperties = setupParamTest("Password123", "25");
        assertEquals(1, invalidProperties.size());
        assertEquals("Only positive numbers are allowed for the build count.", invalidProperties.get(0).getInvalidReason());

        invalidProperties = setupParamTest("1", "25");
        assertEquals(1, invalidProperties.size());
        assertEquals("The minimum build count is 2.", invalidProperties.get(0).getInvalidReason());
    }

    @Test
    void testParamExceedValueUserInput() {
        List<InvalidProperty> invalidProperties = setupParamTest("3", " ");
        assertEquals(1, invalidProperties.size());
        assertEquals("You need to define a threshold value.", invalidProperties.get(0).getInvalidReason());

        invalidProperties = setupParamTest("3", "");
        assertEquals(1, invalidProperties.size());
        assertEquals("You need to define a threshold value.", invalidProperties.get(0).getInvalidReason());

        invalidProperties = setupParamTest("3", "-34");
        assertEquals(1, invalidProperties.size());
        assertEquals("Only positive numbers are allowed for the threshold.", invalidProperties.get(0).getInvalidReason());

        invalidProperties = setupParamTest("3", "Password123");
        assertEquals(1, invalidProperties.size());
        assertEquals("Only positive numbers are allowed for the threshold.", invalidProperties.get(0).getInvalidReason());
    }
}
