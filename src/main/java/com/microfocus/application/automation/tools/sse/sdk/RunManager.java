/*
 * Certain versions of software and/or documents ("Material") accessible here may contain branding from
 * Hewlett-Packard Company (now HP Inc.) and Hewlett Packard Enterprise Company.  As of September 1, 2017,
 * the Material is now offered by Micro Focus, a separately owned and operated company.  Any reference to the HP
 * and Hewlett Packard Enterprise/HPE marks is historical in nature, and the HP and Hewlett Packard Enterprise/HPE
 * marks are the property of their respective owners.
 * __________________________________________________________________
 * MIT License
 *
 * (c) Copyright 2012-2021 Micro Focus or one of its affiliates.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * ___________________________________________________________________
 */

package com.microfocus.application.automation.tools.sse.sdk;

import com.microfocus.application.automation.tools.common.SSEException;
import com.microfocus.application.automation.tools.model.SseModel;
import com.microfocus.application.automation.tools.rest.RestClient;
import com.microfocus.application.automation.tools.sse.common.StringUtils;
import com.microfocus.application.automation.tools.sse.common.XPathUtils;
import com.microfocus.application.automation.tools.sse.result.PublisherFactory;
import com.microfocus.application.automation.tools.sse.result.model.junit.Testsuites;
import com.microfocus.application.automation.tools.sse.sdk.authenticator.AuthenticationTool;
import com.microfocus.application.automation.tools.sse.sdk.handler.PollHandler;
import com.microfocus.application.automation.tools.sse.sdk.handler.PollHandlerFactory;
import com.microfocus.application.automation.tools.sse.sdk.handler.RunHandler;
import com.microfocus.application.automation.tools.sse.sdk.handler.RunHandlerFactory;
import com.microfocus.application.automation.tools.sse.sdk.request.*;

import java.util.*;

/**
 * @author Effi Bar-She'an
 * @author Dani Schreiber
 */
public class RunManager {

    private static final String BVS = "Business Verification Suite";
    private static final String TESTSET = "Test Set";

    private RunHandler _runHandler;
    private PollHandler _pollHandler;
    private Logger _logger;
    private boolean _running = false;
    private boolean _polling = false;

    /**
     * Execute
     */
    public Testsuites execute(RestClient client, Args args, Logger logger)
            throws InterruptedException {
        Testsuites ret = null;
        _logger = logger;
        _running = true;
        if (AuthenticationTool.getInstance().authenticate(client, args.getUsername(), args.getPassword(), args.getUrl(), args.getClientType(), logger)) {
            initialize(args, client);

            if (isValidBvsOrTestSet(client, args) && start(args)) {
                _polling = true;
                if (poll()) {
                    ret =
                            new PublisherFactory().create(
                                    client,
                                    args.getRunType(),
                                    args.getEntityId(),
                                    _runHandler.getRunId()).publish(
                                    _runHandler.getNameSuffix(),
                                    args.getUrl(),
                                    args.getDomain(),
                                    args.getProject(),
                                    logger);
                }
                _polling = false;
            } else {
                ret = new Testsuites(); // empty test suite, containing no tests at all
                this.stop();
            }
        }

        return ret;
    }

    private boolean isValidBvsOrTestSet(RestClient client, Args args) {
        if (args.getRunType().equals(SseModel.BVS)) {
            if (isExistingBvs(client, args)) {
                return isValidBvs(client, args);
            } else {
                _logger.error(String.format("No %s could be found by ID %s!", BVS, args.getEntityId()));
            }
        } else if (args.getRunType().equals(SseModel.TEST_SET)) {
            if (isExistingTestSet(client, args)) {
                return hasTestInstances(client, args.getEntityId());
            } else {
                _logger.error(String.format("No %s of Functional type could be found by ID %s! " +
                        "\nNote: You can run only functional test sets and build verification suites using this task. Check to make sure that the configured ID is valid (and that it is not a performance test ID).", TESTSET, args.getEntityId()));
            }
        } else {
            _logger.error("Unknown run type, please check the configuration.");
        }

        return false;
    }

    private boolean isExistingBvs(RestClient client, Args args) {
        Response res = new GetBvsRequest(client, args.getEntityId()).execute();
        return res != null && res.isOk() && res.getData() != null && XPathUtils.hasResults(res.toString());
    }

    private boolean isExistingTestSet(RestClient client, Args args) {
        Response res = new GetTestSetRequest(client, args.getEntityId()).execute();
        return res != null && res.isOk() && res.getData() != null && XPathUtils.hasResults(res.toString());
    }

    private boolean isValidBvs(RestClient client, Args args) {
        List<String> ids = getBvsTestSetsIds(client, args);
        boolean ok = !ids.isEmpty();

        if (ok) {
            Response res = new GetTestInstancesRequest(client, ids).execute();

            if (res != null && res.isOk() && res.getData() != null) {
                List<String> nonEmptyIds = XPathUtils.getTestSetIds(res.toString());
                ids.removeAll(nonEmptyIds);

                if (!ids.isEmpty()) {
                    ok = false;
                    _logger.error(String.format("%s with ID %s is invalid, the following test sets contain no tests or are not type of functional: %s", BVS, args.getEntityId(), Arrays.toString(ids.toArray())));
                }
            } else {
                ok = false;
                _logger.error(String.format("Cannot get the test sets of %s with ID %s!", BVS, args.getEntityId()));
            }
        } else {
            _logger.error(String.format("%s with ID %s is empty or contains no test sets of type functional!", BVS, args.getEntityId()));
        }

        return ok;
    }

    private List<String> getBvsTestSetsIds(RestClient client, Args args) {
        Response res = new GetBvsTestSetsRequest(client, args.getEntityId()).execute();

        if (res == null || !res.isOk() || res.getData() == null) {
            return Collections.emptyList();
        }

        return XPathUtils.getTestSetIds(res.toString());
    }

    private boolean hasTestInstances(RestClient client, String id) {
        Response res = new GetTestInstancesRequest(client, id).execute();
        boolean ok = res.isOk() && res.getData() != null && XPathUtils.hasResults(res.toString());

        if (!ok) {
            _logger.error(String.format("%s with ID %s is empty or is not of type functional!", TESTSET, id));
        }

        return ok;
    }

    /**
     * Initialize
     */
    private void initialize(Args args, RestClient client) {
        String entityId = args.getEntityId();
        _runHandler = new RunHandlerFactory().create(client, args.getRunType(), entityId);
        _pollHandler = new PollHandlerFactory().create(client, args.getRunType(), entityId);
    }

    /**
     * Poll
     */
    private boolean poll() throws InterruptedException {
        return _pollHandler.poll(_logger);
    }

    /**
     * Stop
     */
    public void stop() {
        _logger.log("Stopping run...");
        if (_runHandler != null) {
            _runHandler.stop();
            _running = false;
        }
        if (_pollHandler != null) {
            _polling = false;
        }
    }

    /**
     * Start
     */
    private boolean start(Args args) {
        boolean ret = false;
        Response response =
                _runHandler.start(
                        args.getDuration(),
                        args.getPostRunAction(),
                        args.getEnvironmentConfigurationId(),
                        args.getCdaDetails());
        if (isOk(response, args)) {
            RunResponse runResponse = getRunResponse(response);
            setRunId(runResponse);
            if (runResponse.isSucceeded()) {
                ret = true;
            }
        }
        logReportUrl(ret, args, response);
        return ret;
    }

    /**
     * Set Run id
     */
    private void setRunId(RunResponse runResponse) {
        String runId = runResponse.getRunId();
        if (StringUtils.isNullOrEmpty(runId)) {
            _logger.log("No run ID");
            throw new SSEException("No run ID");
        } else {
            _runHandler.setRunId(runId);
            _pollHandler.setRunId(runId);
        }
    }

    /**
     * Log report url
     */
    private void logReportUrl(boolean isSucceeded, Args args, Response response) {
        if (isSucceeded) {
            _logger.log(String.format(
                    "%s run report for run id %s is at: %s",
                    args.getRunType(),
                    _runHandler.getRunId(),
                    _runHandler.getReportUrl(args)));
        } else {
            String errMessage = "Failed to prepare timeslot for run. No entity of type " + args.getRunType() + " with id " + args.getEntityId() + " exists.";
            _logger.log(String.format(
                    errMessage
                            + "\nNote: You can run only functional test sets and build verification suites using this plugin. "
                            + "Check to make sure that the configured ID is valid "
                            + "(and that it is not a performance test ID)."));
            _logger.log(response.toString());
        }
    }

    /**
     * Get run response
     */
    private RunResponse getRunResponse(Response response) {
        return _runHandler.getRunResponse(response);
    }

    /**
     * Is response ok
     */
    private boolean isOk(Response response, Args args) {
        boolean ret = false;
        if (response.isOk()) {
            _logger.log(String.format(
                    "Executing %s ID: %s in %s/%s %sDescription: %s",
                    args.getRunType(),
                    args.getEntityId(),
                    args.getDomain(),
                    args.getProject(),
                    StringUtils.NEW_LINE,
                    args.getDescription()));
            ret = true;
        } else {
            Throwable cause = response.getFailure();
            if (cause != null) {
                _logger.log(String.format(
                        "Failed to start %s ID: %s, ALM Server URL: %s (Exception: %s)",
                        args.getRunType(),
                        args.getEntityId(),
                        args.getUrl(),
                        cause.getMessage()));
            } else {
                _logger.log(String.format(
                        "Failed to execute %s ID: %s, ALM Server URL: %s (Response: %s)",
                        args.getRunType(),
                        args.getEntityId(),
                        args.getUrl(),
                        response.getStatusCode()));
            }
        }
        return ret;
    }

    /**
     * Get running
     */
    public boolean getRunning() {
        return _running;
    }

    /**
     * Get polling
     */
    public boolean getPolling() {
        return _polling;
    }

}