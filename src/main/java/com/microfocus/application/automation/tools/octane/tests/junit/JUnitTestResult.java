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

package com.microfocus.application.automation.tools.octane.tests.junit;

import com.hp.octane.integrations.testresults.XmlWritableTestResult;
import com.hp.octane.integrations.uft.ufttestresults.schema.UftResultIterationData;
import com.hp.octane.integrations.uft.ufttestresults.schema.UftResultStepData;
import com.hp.octane.integrations.uft.ufttestresults.schema.UftResultStepParameter;
import com.hp.octane.integrations.utils.SdkStringUtils;
import com.microfocus.application.automation.tools.octane.configuration.SDKBasedLoggerProvider;
import com.microfocus.application.automation.tools.octane.tests.HPRunnerType;
import com.microfocus.application.automation.tools.octane.tests.detection.MFToolsDetectionExtension;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.Serializable;
import java.util.List;

/**
 * Test Run XML writer to mqmTests.xml
 */
final public class JUnitTestResult implements Serializable, XmlWritableTestResult {
    private static Logger logger = SDKBasedLoggerProvider.getLogger(JUnitTestResult.class);
    private final static int DEFAULT_STRING_SIZE = 255;
    private final String moduleName;
    private final String packageName;
    private final String className;
    private final String testName;
    private final String description;
    private TestResultStatus result;
    private long duration;
    private final long started;
    private TestError testError;
    private final String externalReportUrl;
    private final HPRunnerType runnerType;
    private final String externalRunId;
    private final List<UftResultIterationData> uftResultData;
    private final boolean octaneSupportsSteps;

    public JUnitTestResult(String moduleName, String packageName, String className, String testName, TestResultStatus result, long duration, long started, TestError testError, String externalReportUrl, String description, HPRunnerType runnerType, String externalRunId, List<UftResultIterationData> uftResultData, boolean octaneSupportsSteps) {
        this.moduleName = restrictSize(moduleName, DEFAULT_STRING_SIZE);
        this.packageName = restrictSize(packageName, DEFAULT_STRING_SIZE);
        this.className = restrictSize(className, DEFAULT_STRING_SIZE);
        if (StringUtils.isEmpty(testName)) {
            this.testName = "[noName]";
        } else {
            this.testName = restrictSize(testName, DEFAULT_STRING_SIZE);
        }
        this.result = result;
        this.duration = duration;
        this.started = started;
        this.testError = testError;
        this.externalReportUrl = externalReportUrl;
        this.description = description;
        this.runnerType = runnerType;
        this.externalRunId = externalRunId;
        this.uftResultData = uftResultData;
        this.octaneSupportsSteps = octaneSupportsSteps;
    }

    private String restrictSize(String value, int size) {
        String result = value;
        if (value != null && value.length() > size) {
            result = value.substring(0, size);
        }
        return result;
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getClassName() {
        return className;
    }

    public String getTestName() {
        return testName;
    }

    public void setResult(TestResultStatus result) {
        this.result = result;
    }

    public TestResultStatus getResult() {
        return result;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public long getDuration() {
        return duration;
    }

    public long getStarted() {
        return started;
    }

    public void setTestError(TestError testError) {
        this.testError = testError;
    }

    public TestError getTestError() {
        return testError;
    }

    public String getExternalReportUrl() {
        return externalReportUrl;
    }

    public List<UftResultIterationData> getUftResultData() {
        return uftResultData;
    }

    @Override
    public void writeXmlElement(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement("test_run");
        writer.writeAttribute("module", moduleName);
        writer.writeAttribute("package", packageName);
        writer.writeAttribute("class", className);
        writer.writeAttribute("name", testName);
        writer.writeAttribute("duration", String.valueOf(duration));
        writer.writeAttribute("status", result.toPrettyName());
        writer.writeAttribute("started", String.valueOf(started));
        if (externalReportUrl != null && !externalReportUrl.isEmpty()) {
            writer.writeAttribute("external_report_url", externalReportUrl);
        }
        if (externalRunId != null && !externalRunId.isEmpty()) {
            writer.writeAttribute("external_run_id", externalRunId);
        }
        if (HPRunnerType.UFT_MBT.equals(runnerType)) {
            writer.writeAttribute("run_type", MFToolsDetectionExtension.UFT_MBT);
        }
        if (result.equals(TestResultStatus.FAILED) && testError != null) {
            writer.writeStartElement("error");
            writer.writeAttribute("type", String.valueOf(testError.getErrorType()));
            writer.writeAttribute("message", String.valueOf(testError.getErrorMsg()));
            writer.writeCharacters(testError.getStackTraceStr());
            writer.writeEndElement();
        } else if (testError != null && !SdkStringUtils.isEmpty(testError.getErrorMsg())) {//warning case
            writer.writeStartElement("error");
            writer.writeAttribute("message", String.valueOf(testError.getErrorMsg()));
            writer.writeEndElement();
        }

        if (description != null && !description.isEmpty()) {
            writer.writeStartElement("description");
            writer.writeCharacters(description);
            writer.writeEndElement();
        }
        if (octaneSupportsSteps && uftResultData != null) {
            try {
                for (int i = 0; i < uftResultData.size(); i++) {
                    writer.writeStartElement("steps");
                    writer.writeAttribute("iteration", String.valueOf(i + 1));
                    final List<UftResultStepData> steps = uftResultData.get(i).getSteps();
                    for (int j = 0; j < steps.size(); j++) {
                        final UftResultStepData uftResultStepData = steps.get(j);
                        writer.writeStartElement("step");
                        writer.writeAttribute("name", String.valueOf(uftResultStepData.getParents().get(uftResultStepData.getParents().size() - 1)));
                        writer.writeAttribute("duration", String.valueOf(uftResultStepData.getDuration()));
                        writer.writeAttribute("status", uftResultStepData.getResult());
                        final String errorMessage = uftResultStepData.getMessage();
                        if (StringUtils.isNotBlank(errorMessage)) {
                            writer.writeStartElement("error_message");
                            writer.writeCharacters(errorMessage);
                            writer.writeEndElement();
                        }
                        if (uftResultStepData.getInputParameters() != null && !uftResultStepData.getInputParameters().isEmpty()) {
                            writer.writeStartElement("input_parameters");
                            for (int k = 0; k < uftResultStepData.getInputParameters().size(); k++) {
                                final UftResultStepParameter parameter = uftResultStepData.getInputParameters().get(k);
                                writer.writeStartElement("parameter");
                                writer.writeAttribute("name", parameter.getName());
                                writer.writeAttribute("value", parameter.getValue());
                                writer.writeAttribute("type", parameter.getType());
                                writer.writeEndElement();
                            }
                            writer.writeEndElement();
                        }
                        if (uftResultStepData.getOutputParameters() != null && !uftResultStepData.getOutputParameters().isEmpty()) {
                            writer.writeStartElement("output_parameters");
                            for (int k = 0; k < uftResultStepData.getOutputParameters().size(); k++) {
                                final UftResultStepParameter parameter = uftResultStepData.getOutputParameters().get(k);
                                writer.writeStartElement("parameter");
                                writer.writeAttribute("name", parameter.getName());
                                writer.writeAttribute("value", parameter.getValue());
                                writer.writeAttribute("type", parameter.getType());
                                writer.writeEndElement();
                            }
                            writer.writeEndElement();
                        }
                        writer.writeEndElement();
                    }
                    writer.writeEndElement();
                }
            } catch (Exception e) {
                logger.error("Failed to write Steps elements", e);
            }
        }
        writer.writeEndElement();
    }
}
