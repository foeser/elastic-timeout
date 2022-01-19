# elastic-timeout (WIP)
A Teamcity timeout plugin which takes the build history into account.

>**This plugin is not yet finished (well tested) and production ready! Take the source as it is.**

## Purpose

Timeouts should help to deal with hanging builds without any manual intervention (until the issue got investigated).

Teamcity (as of 2021.2.2) offers two ways for setting up timeouts:
- a static value/time can be set as *Common Failure Condition* for the entire execution time (including VCS operations like syncing), see [Set Custom Build Execution Timeout﻿](https://www.jetbrains.com/help/teamcity/2021.2/build-failure-conditions.html#Set+Custom+Build+Execution+Timeout)
- as part of a metric based (in that case the build duration) *Additional Failure Condition* either set by a constant value again or based on the latest (either successful, pinned or finished) or a specific (number or tag) build, see [Fail Build on Metric Change﻿](https://www.jetbrains.com/help/teamcity/2021.2/build-failure-conditions.html#Fail+Build+on+Metric+Change)
  - this metric should not include VCS operations or any non-build times (like artifact resolving/publishing), however there is [currently a bug](https://youtrack.jetbrains.com/issue/TW-74430) to be resolved with 2021.2.3

So you can either just set a constant value, compare to the latest or a specific build. That means that you need to set the threshold rather high
to catch all cases which in turns means that it might not be really useful anymore as you would wait too long before running into the timeout. 
The purpose of this plugin is to allow a more dynamic setup while comparing a set value/threshold against a user defined amount of finished builds (and their average execution time).

This should be helpful in the following situations:
- where you have huge depots/streams and sync times can vary a lot (i.e fresh workspace vs. pre-synced)
  - coming from a gamedev environment I can refer to workspace up to 200GB just source and content
  - however, this should not be an issue anymore once this [bug](https://youtrack.jetbrains.com/issue/TW-74430) is resolved and Teamcity build duration metric is not including VCS operations anymore
- where the execution times can vary because of changes to compile configurations (like adding/removing targets) or due to outdated intermediates
- when adding additional build steps or when changing build steps leading to significantly longer build times
- when the previous execution of a build configuration ran into issues, quiting early but still successful
  - this might totally an edge case and should be fixed elsewhere of course but i.e I had build config using a third party tool which uploads something to somewhere. At one point this tool ran into an issue not being able to upload (some issue with the endpoint) but the error code wasn't managed properly. Instead, it just ran way shorter than normally (returning 0). The next runs where then failing because they exceed the set timeout (build duration metric) just being able to look at the last "successful" build

This list is based on [Fail build on metric change based on average last X builds](https://youtrack.jetbrains.com/issue/TW-73709).

Still I have to admit that the default timeout implementations are in general good enough, and it heavily depends on your environment
and potential edge cases you want or need to cover. If for example your repo is not very big and your sources compiles in seconds then you can (should) stick to what Teamcity offers.

## To be done prior to release

- [ ] User input validation
- [ ] Allow stopping builds (depending on user settings)
- [ ] Adapt UI and logs to be closer to Teamcity 
- [ ] Testing and writing Tests
- [ ] DSL functionality

## Trivia

The plugin is referring to the Jenkins [Build Timeout plugin](https://jenkinsci.github.io/job-dsl-plugin/#path/javaposse.jobdsl.dsl.helpers.wrapper.WrapperContext.timeout-elastic)  :)


