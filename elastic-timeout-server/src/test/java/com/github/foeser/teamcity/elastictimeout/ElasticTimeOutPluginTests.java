package com.github.foeser.teamcity.elastictimeout;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.github.foeser.teamcity.elastictimeout.utils.MemoryAppender;
import org.slf4j.LoggerFactory;

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.util.EventDispatcher;

import org.jmock.Mockery;
import org.jmock.Expectations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

// https://github.com/jmock-developers/jmock-library/blob/master/jmock/src/test/java/org/jmock/test/unit/lib/concurrent/DeterministicSchedulerTests.java#L177

public class ElasticTimeOutPluginTests extends BaseTestCase {
    private Mockery context;
    private BuildEventListener buildEventListener;
    private BuildTimeoutHandler buildTimeoutHandler;
    private SBuildFeatureDescriptor mockSBuildFeatureDescriptor;
    private BuildHistory buildHistory;
    MemoryAppender memoryAppender;

    @BeforeMethod
    @Override
    public void setUp() throws Exception {
        super.setUp();
        context = new Mockery();
        final EventDispatcher<BuildServerListener> eventDispatcher = EventDispatcher.create(BuildServerListener.class);
        final ExecutorServices executorServices = context.mock(ExecutorServices.class);
        final RunningBuildsManager runningBuildsManager = context.mock(RunningBuildsManager.class);
        buildHistory = context.mock(BuildHistory.class);
        mockSBuildFeatureDescriptor = context.mock(SBuildFeatureDescriptor.class);

        context.checking(new Expectations() {
            {
                ScheduledExecutorService ses = new ScheduledThreadPoolExecutor(1);
                allowing(executorServices).getNormalExecutorService(); will(returnValue(ses));
            }
        });
        buildTimeoutHandler = new BuildTimeoutHandler(executorServices, runningBuildsManager, buildHistory);
        buildEventListener = new BuildEventListener(eventDispatcher, buildTimeoutHandler);

        // Todo: Test if actually logging on Teamcity works (maybe switch to AppenderSkeleton then: https://stackoverflow.com/a/1828268/1072693)
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
    public void addBuildWithoutBuildFeature() {
        final SRunningBuild mockSRunningBuild = context.mock(SRunningBuild.class);
        context.checking(new Expectations() {
           {
               // let's return an empty array
               oneOf(mockSRunningBuild).getBuildFeaturesOfType(ElasticTimeoutFailureCondition.TYPE); will (returnValue( new ArrayList<SBuildFeatureDescriptor>()));
           }
        });
        buildEventListener.buildStarted(mockSRunningBuild);
        assertEquals(0, buildTimeoutHandler.getCurrentBuildsConsidered());
        assertEquals(1, memoryAppender.search(String.format("%s [%s]", "Either none or more then one enabled AvgBuildTimeFailureCondition feature (failure condition) in that build", mockSRunningBuild)).size());
    }
    @Test
    public void removeBuild() {
        final SRunningBuild mockSRunningBuild = context.mock(SRunningBuild.class);
        // Todo: get the default params from the actual class/object
        final Map<String, String> elasticTimeoutFailureConditionParameters = Map.ofEntries(
                new AbstractMap.SimpleEntry(ElasticTimeoutFailureCondition.PARAM_BUILD_COUNT, "3"),
                new AbstractMap.SimpleEntry(ElasticTimeoutFailureCondition.PARAM_STATUS, "Successful"),
                new AbstractMap.SimpleEntry(ElasticTimeoutFailureCondition.PARAM_EXCEED_VALUE, "25"),
                new AbstractMap.SimpleEntry(ElasticTimeoutFailureCondition.PARAM_EXCEED_UNIT, "seconds"),
                new AbstractMap.SimpleEntry(ElasticTimeoutFailureCondition.PARAM_STOP_BUILD, "true")
        );
        final SFinishedBuild build = context.mock(SFinishedBuild.class);

        context.checking(new Expectations() {
            {
                atLeast(2).of(mockSRunningBuild).getBuildFeaturesOfType(ElasticTimeoutFailureCondition.TYPE); will (returnValue(Collections.singleton(mockSBuildFeatureDescriptor)));
                oneOf(mockSBuildFeatureDescriptor).getParameters(); will (returnValue(elasticTimeoutFailureConditionParameters));
                oneOf(buildHistory).getEntriesBefore(mockSRunningBuild, true); will (returnValue(Arrays.asList(build, build, build, build)));
                //atLeast(3).of (build).getBuildStatus(); will(returnValue(Status.NORMAL));
                atLeast(4).of (build).getDuration();
                will(onConsecutiveCalls(
                        returnValue(10L),
                        returnValue(20L),
                        returnValue(30L)));
                atLeast(2).of(mockSRunningBuild).getBuildId(); will (returnValue(1L));
            }
        });
        buildEventListener.buildStarted(mockSRunningBuild);
        assertEquals(1, buildTimeoutHandler.getCurrentBuildsConsidered());
        buildEventListener.beforeBuildFinish(mockSRunningBuild);
        assertEquals(0, buildTimeoutHandler.getCurrentBuildsConsidered());

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
    @Test
    void testUserInput() {
        // add build with missconfigured feature
    }
    @Test
    void successfulOnly() {
        // add build and test scenario with succesful and all builds
    }
    @Test
    void testVCSTimes() {
        // add build and test if build duration includes VCS or artifact operations
    }
}
