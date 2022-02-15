package com.github.foeser.teamcity.elastictimeout;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.github.foeser.teamcity.elastictimeout.schedulers.ManualScheduler;
import com.github.foeser.teamcity.elastictimeout.utils.MemoryAppender;
import jetbrains.buildServer.BuildProblemData;
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

import static com.github.foeser.teamcity.elastictimeout.ElasticTimeoutFailureCondition.*;

// https://github.com/jmock-developers/jmock-library/blob/master/jmock/src/test/java/org/jmock/test/unit/lib/concurrent/DeterministicSchedulerTests.java#L177

public class ElasticTimeOutPluginTests extends BaseTestCase {
    private Mockery context;
    private BuildEventListener buildEventListener;
    private BuildTimeoutHandler buildTimeoutHandler;
    private SBuildFeatureDescriptor mockSBuildFeatureDescriptor;
    private BuildHistory buildHistory;
    private MemoryAppender memoryAppender;
    private MemoryAppender memoryAppenderBuildTimeoutHandler;
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
        memoryAppenderBuildTimeoutHandler = new MemoryAppender();
        memoryAppenderBuildTimeoutHandler.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        Logger loggerBuildEventListener = (Logger) LoggerFactory.getLogger(BuildEventListener.class);
        loggerBuildEventListener.setLevel(Level.DEBUG);
        loggerBuildEventListener.addAppender(memoryAppender);
        Logger loggerBuildTimeoutHandler = (Logger) LoggerFactory.getLogger(BuildTimeoutHandler.class);
        loggerBuildTimeoutHandler.setLevel(Level.DEBUG);
        loggerBuildTimeoutHandler.addAppender(memoryAppenderBuildTimeoutHandler);
        memoryAppender.start();
        memoryAppenderBuildTimeoutHandler.start();
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
        assertEquals(1, memoryAppender.search(String.format("%s doesn't have the elastic timeout failure condition set or enabled. Skipping...", mockSRunningBuild)).size());
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
        // invoke scheduler twice for this test (should return 13s and 38s of 45s timeout)
        manualScheduler.invoke();
        manualScheduler.invoke();
        // finish build early
        buildEventListener.beforeBuildFinish(mockSRunningBuild);
        assertEquals(1, memoryAppenderBuildTimeoutHandler.search(String.format("%s finished and will no longer be considered.", mockSRunningBuild)).size());
        // invoke again after build has finished verifying that there are no builds in consideration anymore
        manualScheduler.invoke();
        assertEquals(1, memoryAppenderBuildTimeoutHandler.search("No builds to check, quitting early").size());
        assertEquals(0, buildTimeoutHandler.getCurrentBuildsInConsideration());
    }
    @Test
    // add build with too few builds in history; nothing should happen
    void addBuildWithoutHistory() {
        final SRunningBuild mockSRunningBuild = context.mock(SRunningBuild.class);
        final Map<String, String> elasticTimeoutFailureConditionParameters = failureCondition.getDefaultParameters();
        final SFinishedBuild mockSFinishedBuild = context.mock(SFinishedBuild.class);

        context.checking(new Expectations() {
            {
                // get called twice, once while adding (buildStarted) and once while removing (beforeBuildFinish)
                atLeast(2).of(mockSRunningBuild).getBuildFeaturesOfType(ElasticTimeoutFailureCondition.TYPE); will (returnValue(Collections.singleton(mockSBuildFeatureDescriptor)));
                oneOf(mockSBuildFeatureDescriptor).getParameters(); will (returnValue(elasticTimeoutFailureConditionParameters));
                // based on plugins default settings we add three builds to the history and querying accordingly three times the duration (returning different values each time)
                oneOf(buildHistory).getEntriesBefore(mockSRunningBuild, true); will (returnValue(Arrays.asList(mockSFinishedBuild, mockSFinishedBuild)));
            }
        });
        buildEventListener.buildStarted(mockSRunningBuild);
        // since there are just two builds in history this build shouldn't be considered
        assertEquals(0, buildTimeoutHandler.getCurrentBuildsInConsideration());
        assertEquals(1, memoryAppenderBuildTimeoutHandler.search(String.format("[%s] has elastic timout enabled but doesn't have enough builds in history to consider (%d/%d). Skipping...", mockSRunningBuild, 2, 3)).size());
    }
    @Test
    // add build, run into timeout and test if build stopped and got removed from map
    // could think about testing history with "any" build but since its mocked anyway it doesn't make so much sense
    void stopBuild() {
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
                        returnValue(46L)));
                oneOf(mockSRunningBuild).stop(with(any(DummyUser.class)), with(any(String.class)));
                oneOf(mockSRunningBuild).addBuildProblem(with(any(BuildProblemData.class)));
            }
        });
        buildEventListener.buildStarted(mockSRunningBuild);
        assertEquals(1, buildTimeoutHandler.getCurrentBuildsInConsideration());
        // invoke scheduler twice for this test (should return 13s and 46s of 45s timeout)
        manualScheduler.invoke();
        // this should result in timeout
        manualScheduler.invoke();
        assertEquals(0, buildTimeoutHandler.getCurrentBuildsInConsideration());
        assertEquals(1, memoryAppenderBuildTimeoutHandler.search(String.format("%s is running already %d and exceed maximum allowed time of %d and got stopped.", mockSRunningBuild, 46, 45)).size());
        // simulate finishing event and check again on log
        buildEventListener.beforeBuildFinish(mockSRunningBuild);
        assertEquals(1, memoryAppenderBuildTimeoutHandler.search(String.format("%s was removed earlier already from consideration due to timeout.", mockSRunningBuild)).size());
        manualScheduler.invoke();
        assertEquals(1, memoryAppenderBuildTimeoutHandler.search("No builds to check, quitting early").size());
    }
    @Test
    // add build, don't stop and check if build problem was added and if it got removed from map
    void addJustBuildProblem() {
        final SRunningBuild mockSRunningBuild = context.mock(SRunningBuild.class);
        final Map<String, String> elasticTimeoutFailureConditionParameters = failureCondition.getDefaultParameters();
        // switch param to not stop build (but just add build problem)
        elasticTimeoutFailureConditionParameters.put(PARAM_STOP_BUILD, "false");
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
                        returnValue(46L)));
                oneOf(mockSRunningBuild).addBuildProblem(with(any(BuildProblemData.class)));
            }
        });
        buildEventListener.buildStarted(mockSRunningBuild);
        assertEquals(1, buildTimeoutHandler.getCurrentBuildsInConsideration());
        // invoke scheduler twice for this test (should return 13s and 46s of 45s timeout)
        manualScheduler.invoke();
        // this should result in timeout
        manualScheduler.invoke();
        assertEquals(0, buildTimeoutHandler.getCurrentBuildsInConsideration());
        assertEquals(1, memoryAppenderBuildTimeoutHandler.search(String.format("%s is running already %d and exceed maximum allowed time of %d and got annotated with build problem.", mockSRunningBuild, 46, 45)).size());
        // simulate finishing event and check again on log
        buildEventListener.beforeBuildFinish(mockSRunningBuild);
        assertEquals(1, memoryAppenderBuildTimeoutHandler.search(String.format("%s was removed earlier already from consideration due to timeout.", mockSRunningBuild)).size());
        manualScheduler.invoke();
        assertEquals(1, memoryAppenderBuildTimeoutHandler.search("No builds to check, quitting early").size());
    }
    @Test
    void testPercentageCalculation() {
        // add build and test timeout based on percentage calculations
    }
    @Test
    void testFixedValueCalculation() {
        // add build and test timeout based on fixed values calculations
    }
}
