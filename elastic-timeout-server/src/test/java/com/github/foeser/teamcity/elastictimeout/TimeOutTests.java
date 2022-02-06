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

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class TimeOutTests extends BaseTestCase {
    //private SRunningBuild sRunningBuild;
    private SBuild sBuild;
    private Mockery context;
    private BuildEventListener buildEventListener;
    private BuildTimeoutHandler buildTimeoutHandler;
    private SBuildFeatureDescriptor mockSBuildFeatureDescriptor;

    private SFinishedBuild build3;
    private SFinishedBuild build2;
    private SFinishedBuild build1;
    MemoryAppender memoryAppender;

    @BeforeMethod
    @Override
    public void setUp() throws Exception {
        super.setUp();
        context = new Mockery();
        final EventDispatcher<BuildServerListener> eventDispatcher = EventDispatcher.create(BuildServerListener.class);
        final ExecutorServices executorServices = context.mock(ExecutorServices.class);
        final RunningBuildsManager runningBuildsManager = context.mock(RunningBuildsManager.class);
        final BuildHistory buildHistory = context.mock(BuildHistory.class);
        //sRunningBuild = context.mock(SRunningBuild.class);
        sBuild = context.mock(SBuild.class);
        mockSBuildFeatureDescriptor = context.mock(SBuildFeatureDescriptor.class);
        build1 = context.mock(SFinishedBuild.class, "Build1");
        build2 = context.mock(SFinishedBuild.class, "Build2");
        build3 = context.mock(SFinishedBuild.class, "Build3");

        context.checking(new Expectations() {
            {
                allowing(mockSBuildFeatureDescriptor).getType(); will (returnValue(ElasticTimeoutFailureCondition.TYPE));
                //allowing(sRunningBuild).getBuildFeaturesOfType(ElasticTimeoutFailureCondition.TYPE); will (returnValue(Collections.singleton(mockSBuildFeatureDescriptor)));
                allowing(mockSBuildFeatureDescriptor).getParameters(); will (returnValue(Collections.singletonMap(ElasticTimeoutFailureCondition.PARAM_BUILD_COUNT, "99")));
                ScheduledExecutorService ses = new ScheduledThreadPoolExecutor(1);
                allowing(executorServices).getNormalExecutorService(); will(returnValue(ses));
                //allowing(buildHistory.getEntriesBefore(sBuild, true)); will (returnValue(Arrays.asList(build1, build2, build3)));
            }});
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
           }});
        buildEventListener.buildStarted(mockSRunningBuild);
        assertEquals(0, buildTimeoutHandler.getCurrentBuildsConsidered());
        assertEquals(1, memoryAppender.search(String.format("%s [%s]", "Either none or more then one enabled AvgBuildTimeFailureCondition feature (failure condition) in that build", mockSRunningBuild)).size());
    }
    @Test
    public void removeBuild() {
        // add build, remove it earlier
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
}
