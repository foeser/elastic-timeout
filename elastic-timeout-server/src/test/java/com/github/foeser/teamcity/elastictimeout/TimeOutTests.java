package com.github.foeser.teamcity.elastictimeout;

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
       assertTrue(buildTimeoutHandler.getCurrentBuildsConsidered() == 0);
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
