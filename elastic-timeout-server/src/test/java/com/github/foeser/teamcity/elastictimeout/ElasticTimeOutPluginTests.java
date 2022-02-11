package com.github.foeser.teamcity.elastictimeout;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.github.foeser.teamcity.elastictimeout.schedulers.ManualScheduler;
import com.github.foeser.teamcity.elastictimeout.utils.MemoryAppender;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.slf4j.LoggerFactory;

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.EventDispatcher;

import org.jmock.Mockery;
import org.jmock.Expectations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.*;

import static com.github.foeser.teamcity.elastictimeout.ElasticTimeoutFailureCondition.PARAM_BUILD_COUNT;
import static com.github.foeser.teamcity.elastictimeout.ElasticTimeoutFailureCondition.PARAM_EXCEED_VALUE;

// https://github.com/jmock-developers/jmock-library/blob/master/jmock/src/test/java/org/jmock/test/unit/lib/concurrent/DeterministicSchedulerTests.java#L177

public class ElasticTimeOutPluginTests extends BaseTestCase {
    private Mockery context;
    private BuildEventListener buildEventListener;
    private BuildTimeoutHandler buildTimeoutHandler;
    private SBuildFeatureDescriptor mockSBuildFeatureDescriptor;
    private BuildHistory buildHistory;
    private MemoryAppender memoryAppender;
    private ManualScheduler manualScheduler;
    private RunningBuildsManager runningBuildsManager;
    private ElasticTimeoutFailureCondition failureCondition;

    @BeforeMethod
    @Override
    public void setUp() throws Exception {
        super.setUp();
        context = new Mockery();
        final EventDispatcher<BuildServerListener> eventDispatcher = EventDispatcher.create(BuildServerListener.class);
        runningBuildsManager = context.mock(RunningBuildsManager.class);
        buildHistory = context.mock(BuildHistory.class);
        mockSBuildFeatureDescriptor = context.mock(SBuildFeatureDescriptor.class);

        manualScheduler = new ManualScheduler();
        buildTimeoutHandler = new BuildTimeoutHandler(manualScheduler, runningBuildsManager, buildHistory);
        buildEventListener = new BuildEventListener(eventDispatcher, buildTimeoutHandler);
        PluginDescriptor mockPluginDescriptor = context.mock(PluginDescriptor.class);
        context.checking(new Expectations() {
            {
                oneOf(mockPluginDescriptor).getPluginResourcesPath("ElasticTimeoutFailureConditionSettings.jsp"); will (returnValue(""));
            }
        });
        failureCondition = new ElasticTimeoutFailureCondition(mockPluginDescriptor);

        // Todo: Test if actually logging on Teamcity works (maybe switch to AppenderSkeleton then: https://stackoverflow.com/a/1828268/1072693)
        // Todo: no it doesn't with slf4j :(
        // in order to just get simple logs printed to console we can set the log Level to DEBUG (as the root level set in log4j.xml get set later back to WARN, see debug output)
        // also logback (at least context) would need to be removed and assuming using org.apache.log4j and not org.slf4j
        //org.apache.log4j.Logger.getLogger(BuildEventListener.class).setLevel(org.apache.log4j.Level.DEBUG);

        memoryAppender = new MemoryAppender();
        memoryAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        Logger logger = (Logger) LoggerFactory.getLogger(BuildEventListener.class);
        logger.setLevel(Level.DEBUG);
        logger.addAppender(memoryAppender);
        memoryAppender.start();
    }

    @Test
    // add build (configuration) without ElasticTimeout build feature enabled, expected result would be that it won't get considered at all
    public void addBuildWithoutBuildFeature() {
        final SRunningBuild mockSRunningBuild = context.mock(SRunningBuild.class);
        context.checking(new Expectations() {
           {
               // let's simply return an empty array
               oneOf(mockSRunningBuild).getBuildFeaturesOfType(ElasticTimeoutFailureCondition.TYPE); will (returnValue( new ArrayList<SBuildFeatureDescriptor>()));
           }
        });
        buildEventListener.buildStarted(mockSRunningBuild);
        assertEquals(0, buildTimeoutHandler.getCurrentBuildsInConsideration());
        assertEquals(1, memoryAppender.search(String.format("%s [%s]", "Either none or more then one enabled AvgBuildTimeFailureCondition feature (failure condition) in that build", mockSRunningBuild)).size());
    }
    @Test
    // add valid build (timeout feature enabled with proper build history) but finish it earlier, expected result would be that it get removed from consideration
    public void removeBuild() {
        final SRunningBuild mockSRunningBuild = context.mock(SRunningBuild.class);
        final Map<String, String> elasticTimeoutFailureConditionParameters = failureCondition.getDefaultParameters();
        final SFinishedBuild mockSFinishedBuild = context.mock(SFinishedBuild.class);

        context.checking(new Expectations() {
            {
                // get called twice, once while adding (buildStarted) and once while removing (beforeBuildFinish)
                atLeast(2).of(mockSRunningBuild).getBuildFeaturesOfType(ElasticTimeoutFailureCondition.TYPE); will (returnValue(Collections.singleton(mockSBuildFeatureDescriptor)));
                oneOf(mockSBuildFeatureDescriptor).getParameters(); will (returnValue(elasticTimeoutFailureConditionParameters));
                // based on plugins default settings we add three builds to the history and querying accordingly three times the duration (returning different values each time)
                oneOf(buildHistory).getEntriesBefore(mockSRunningBuild, true); will (returnValue(Arrays.asList(mockSFinishedBuild, mockSFinishedBuild, mockSFinishedBuild)));
                atLeast(3).of (mockSFinishedBuild).getDuration();
                will(onConsecutiveCalls(
                        returnValue(10L),
                        returnValue(20L),
                        returnValue(30L)));
                // get called twice, within scheduler and once while removing
                atLeast(2).of(mockSRunningBuild).getBuildId(); will (returnValue(1L));
                // get invoked twice within scheduler, and return below timeout value (45s)
                atLeast(2).of(runningBuildsManager).findRunningBuildById(1L); will (returnValue(mockSRunningBuild));
                atLeast(2).of(mockSRunningBuild).getDuration();
                will(onConsecutiveCalls(
                        returnValue(13L),
                        returnValue(38L)));
                // not working :( while actually using oneOf() would work, no clue...
                //never(mockSRunningBuild).stop(with(any(DummyUser.class)), with(any(String.class)));
            }
        });
        buildEventListener.buildStarted(mockSRunningBuild);
        assertEquals(1, buildTimeoutHandler.getCurrentBuildsInConsideration());
        // ToDo: think of asserting for the log done by BuildEventListener (memory adapter)
        // invoke scheduler twice
        manualScheduler.invoke();
        manualScheduler.invoke();
        // finish build early
        buildEventListener.beforeBuildFinish(mockSRunningBuild);
        // invoke again after build has finished verifying that there are no builds in consideration anymore
        manualScheduler.invoke();
        assertEquals(0, buildTimeoutHandler.getCurrentBuildsInConsideration());

    }
    @Test
    void addBuildWithoutHistory() {
       // add build with too few builds in history -> nothing should happen
    }
    @Test
    void stopBuild() {
        // add build, run into timeout and test if build stopped and got removed from map
    }
    @Test
    void addBuildProblem() {
        // add build, don't stop and check if buildproblem was added and if it got removed from map
    }
    @Test
    void testPercentageCalculation() {
        // add build and test timeout based on percentage calculations
    }
    @Test
    void testFixedValueCalculation() {
        // add build and test timeout based on fixed values calculations
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
    @Test
    void successfulOnly() {
        // add build and test scenario with succesful and all builds
    }
}
