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

package com.microfocus.application.automation.tools.run;

import com.microfocus.application.automation.tools.model.*;
import com.microfocus.application.automation.tools.octane.executor.UftConstants;
import com.microfocus.application.automation.tools.uft.model.FilterTestsModel;
import com.microfocus.application.automation.tools.settings.AlmServerSettingsGlobalConfiguration;
import com.microfocus.application.automation.tools.uft.model.SpecifyParametersModel;
import hudson.*;

import hudson.model.*;

import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Stream;

import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.microfocus.application.automation.tools.AlmToolsUtils;
import com.microfocus.application.automation.tools.EncryptionUtils;
import com.microfocus.application.automation.tools.run.AlmRunTypes.RunType;

import static com.microfocus.application.automation.tools.Messages.CompanyName;
import static com.microfocus.application.automation.tools.Messages.RunFromAlmBuilderStepName;

public class RunFromAlmBuilder extends Builder implements SimpleBuildStep {

    public RunFromAlmModel runFromAlmModel;
    private boolean isFilterTestsEnabled;
    private boolean areParametersEnabled;
    private FilterTestsModel filterTestsModel;
    private SpecifyParametersModel specifyParametersModel;
    private final static String HpToolsLauncher_SCRIPT_NAME = "HpToolsLauncher.exe";
    private String ResultFilename = "ApiResults.xml";
    private String ParamFileName = "ApiRun.txt";
    private AlmServerSettingsModel almServerSettingsModel;

    @DataBoundConstructor
    public RunFromAlmBuilder(
            String almServerName,
            String almCredentialsScope,
            String almUserName,
            String almPassword,
            String almDomain,
            String almProject,
            String almTestSets,
            String almRunResultsMode,
            String almTimeout,
            String almRunMode,
            String almRunHost,
            String almClientID,
            String almApiKey,
            boolean isSSOEnabled,
            boolean isFilterTestsEnabled,
            boolean areParametersEnabled,
            FilterTestsModel filterTestsModel,
            SpecifyParametersModel specifyParametersModel,
            AlmServerSettingsModel almServerSettingsModel) {

        this.isFilterTestsEnabled = isFilterTestsEnabled;
        this.areParametersEnabled = areParametersEnabled;
        this.filterTestsModel = filterTestsModel;
        this.specifyParametersModel = specifyParametersModel;
        this.almServerSettingsModel = almServerSettingsModel;
        CredentialsScope almCredScope = StringUtils.isBlank(almCredentialsScope) ?
                findMostSuitableCredentialsScope(almServerName, almUserName, almClientID, isSSOEnabled) :
                CredentialsScope.valueOf(almCredentialsScope.toUpperCase());

        runFromAlmModel =
                new RunFromAlmModel(
                        almServerName,
                        almUserName,
                        almPassword,
                        almDomain,
                        almProject,
                        almTestSets,
                        almRunResultsMode,
                        almTimeout,
                        almRunMode,
                        almRunHost,
                        isSSOEnabled,
                        almClientID,
                        almApiKey,
                        almCredScope);
    }

    public CredentialsScope getCredentialsScopeOrDefault() {
        CredentialsScope scope = runFromAlmModel.getCredentialsScope();
        return scope == null ?
                findMostSuitableCredentialsScope(getAlmServerName(), getAlmUserName(), getAlmClientID(), getIsSSOEnabled()) :
                scope;
    }

    private AlmServerSettingsModel findAlmServerSettingsModel(String serverName) {
        Stream<AlmServerSettingsModel> models = Arrays.stream(AlmServerSettingsGlobalConfiguration.getInstance().getInstallations());
        return models.filter(m -> m.getAlmServerName().equals(serverName)).findFirst().orElse(null);
    }

    private boolean isUserNameDefinedAtSystemLevel(String serverName, String userName) {
        AlmServerSettingsModel model = findAlmServerSettingsModel(serverName);
        if (model != null) {
            return model.getAlmCredentials().stream().anyMatch(c -> c.getAlmUsername().equals(userName));
        }
        return false;
    }

    private boolean isClientIdDefinedAtSystemLevel(String serverName, String clientId) {
        AlmServerSettingsModel model = findAlmServerSettingsModel(serverName);
        if (model != null) {
            return model.getAlmSSOCredentials().stream().anyMatch(c -> c.getAlmClientID().equals(clientId));
        }
        return false;
    }

    private CredentialsScope findMostSuitableCredentialsScope(String serverName, String userName, String clientId, boolean isSSOEnabled) {
        if (isSSOEnabled) {
            return isClientIdDefinedAtSystemLevel(serverName, clientId) ? CredentialsScope.SYSTEM : CredentialsScope.JOB;
        } else {
            return isUserNameDefinedAtSystemLevel(serverName, userName) ? CredentialsScope.SYSTEM : CredentialsScope.JOB;
        }
    }

    public String getAlmServerName() {
        return runFromAlmModel.getAlmServerName();
    }

    public boolean getIsSSOEnabled() {
        return runFromAlmModel.isSSOEnabled();
    }

    public void setIsSSOEnabled(Boolean isSSOEnabled) {
        runFromAlmModel.setIsSSOEnabled(isSSOEnabled);
    }

    /* This setter seems to be useless, it only seems to generate an unnecessary object in pipeline script, of type RunFromAlmModel
    Also, it is already set in the constructor above
    @DataBoundSetter
    public void setRunFromAlmModel(RunFromAlmModel runFromAlmModel){
        this.runFromAlmModel = runFromAlmModel;
    }*/

    @DataBoundSetter
    public void setAlmServerSettingsModel(AlmServerSettingsModel almServerSettingsModel) {
        this.almServerSettingsModel = almServerSettingsModel;
    }

    //IMPORTANT: most properties are used by config.jelly and / or by pipeline-syntax generator
    public String getAlmCredentialsScope() {
        return runFromAlmModel.getCredentialsScopeValue();
    }

    public String getAlmUserName() {
        return runFromAlmModel.getAlmUserName();
    }

    public String getAlmPassword() {
        return runFromAlmModel.getPasswordEncryptedValue();
    }

    public String getAlmClientID() {
        return runFromAlmModel.getAlmClientID();
    }

    public String getAlmApiKey() {
        return runFromAlmModel.getApiKeyEncryptedValue();
    }

    public String getAlmDomain() {
        return runFromAlmModel.getAlmDomain();
    }

    public String getAlmProject() {
        return runFromAlmModel.getAlmProject();
    }

    public String getAlmTestSets() {
        return runFromAlmModel.getAlmTestSets();
    }

    public String getAlmRunResultsMode() {
        return runFromAlmModel.getAlmRunResultsMode();
    }

    public String getAlmTimeout() {
        return runFromAlmModel.getAlmTimeout();
    }

    public String getAlmRunMode() {
        return runFromAlmModel.getAlmRunMode();
    }

    public String getAlmRunHost() {
        return runFromAlmModel.getAlmRunHost();
    }

    public boolean getIsFilterTestsEnabled() {
        return isFilterTestsEnabled;
    }

    @DataBoundSetter
    public void setIsFilterTestsEnabled(boolean isFilterTestsEnabled) {
        this.isFilterTestsEnabled = isFilterTestsEnabled;
    }

    public FilterTestsModel getFilterTestsModel() {
        return filterTestsModel;
    }

    @DataBoundSetter
    public void setFilterTestsModel(FilterTestsModel filterTestsModel) {
        this.filterTestsModel = filterTestsModel;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
            throws InterruptedException, IOException {

        // get the alm server settings
        AlmServerSettingsModel almServerSettingsModel = getAlmServerSettingsModel();

        if (almServerSettingsModel == null) {
            listener.fatalError("An ALM server is not defined. Go to Manage Jenkins->Configure System and define your ALM server under Application Lifecycle Management");

            // set pipeline stage as failure in case if ALM server was not configured
            build.setResult(Result.FAILURE);

            return;
        }

        EnvVars env = null;
        try {
            env = build.getEnvironment(listener);
        } catch (IOException e2) {
            // TODO Auto-generated catch block
            e2.printStackTrace();
        }
        VariableResolver<String> varResolver = new VariableResolver.ByMap<String>(build.getEnvironment(listener));

        // now merge them into one list
        Properties mergedProperties = new Properties();

        mergedProperties.putAll(almServerSettingsModel.getProperties());
        mergedProperties.putAll(runFromAlmModel.getProperties(env, varResolver));

        CredentialsScope scope = getCredentialsScopeOrDefault();
        String encAlmPass = "";
        try {
            String almPassword = runFromAlmModel.getPasswordPlainText();
            if (scope == CredentialsScope.SYSTEM) {
                Optional<CredentialsModel> cred = almServerSettingsModel.getAlmCredentials().stream().filter(c -> c.getAlmUsername().equals(runFromAlmModel.getAlmUserName())).findFirst();
                if (cred.isPresent()) {
                    almPassword = cred.get().getAlmPasswordPlainText();
                }
            }
            encAlmPass = EncryptionUtils.Encrypt(almPassword, EncryptionUtils.getSecretKey());

            mergedProperties.remove(RunFromAlmModel.ALM_PASSWORD_KEY);
            mergedProperties.put(RunFromAlmModel.ALM_PASSWORD_KEY, encAlmPass);
        } catch (Exception e) {
            build.setResult(Result.FAILURE);
            listener.fatalError("problem with qcPassword encryption");
        }

        String encAlmApiKey = "";
        try {
            String almApiKeySecret = runFromAlmModel.getApiKeyPlainText();
            if (scope == CredentialsScope.SYSTEM) {
                Optional<SSOCredentialsModel> cred = almServerSettingsModel.getAlmSSOCredentials().stream().filter(c -> c.getAlmClientID().equals(runFromAlmModel.getAlmClientID())).findFirst();
                if (cred.isPresent()) {
                    almApiKeySecret = cred.get().getAlmApiKeySecretPlainText();
                }
            }

            encAlmApiKey = EncryptionUtils.Encrypt(almApiKeySecret, EncryptionUtils.getSecretKey());
            mergedProperties.remove(RunFromAlmModel.ALM_API_KEY_SECRET);
            mergedProperties.put(RunFromAlmModel.ALM_API_KEY_SECRET, encAlmApiKey);
            mergedProperties.put("almClientID", getAlmClientID());
        } catch (Exception e) {
            build.setResult(Result.FAILURE);
            listener.fatalError("problem with apiKey encryption");
        }

        if (isFilterTestsEnabled) {
            filterTestsModel.addProperties(mergedProperties);
        } else {
            mergedProperties.put("FilterTests", "false");
        }

        Date now = new Date();
        Format formatter = new SimpleDateFormat("ddMMyyyyHHmmssSSS");
        String time = formatter.format(now);

        // get a unique filename for the params file
        ParamFileName = "props" + time + ".txt";
        ResultFilename = "Results" + time + ".xml";
        //KillFileName = "stop" + time + ".txt";

        //params used when run with Pipeline
        ParametersAction parameterAction = build.getAction(ParametersAction.class);
        List<ParameterValue> newParams = (parameterAction != null) ? new ArrayList<>(parameterAction.getAllParameters()) : new ArrayList<>();
        newParams.add(new StringParameterValue("buildStepName", "RunFromAlmBuilder"));
        newParams.add(new StringParameterValue("resultsFilename", ResultFilename));
        build.addOrReplaceAction(new ParametersAction(newParams));

        mergedProperties.put("runType", RunType.Alm.toString());
        mergedProperties.put("resultsFilename", ResultFilename);

        if (areParametersEnabled) {
            try {
                specifyParametersModel.addProperties(mergedProperties, "TestSet");
            } catch (Exception e) {
                listener.error("Error occurred while parsing parameter input, reverting back to empty array.");
            }
        }

        // get properties serialized into a stream
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            mergedProperties.store(stream, "");
        } catch (IOException e) {
            build.setResult(Result.FAILURE);
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        String propsSerialization = stream.toString();
        InputStream propsStream = IOUtils.toInputStream(propsSerialization);

        // get the remote workspace filesys
        FilePath projectWS = workspace;

        // Get the URL to the Script used to run the test, which is bundled
        // in the plugin

        URL cmdExeUrl =
                Hudson.getInstance().pluginManager.uberClassLoader.getResource(HpToolsLauncher_SCRIPT_NAME);
        if (cmdExeUrl == null) {
            listener.fatalError(HpToolsLauncher_SCRIPT_NAME + " not found in resources");
            return;
        }

        FilePath propsFileName = projectWS.child(ParamFileName);
        FilePath CmdLineExe = projectWS.child(HpToolsLauncher_SCRIPT_NAME);

        try {
            // create a file for the properties file, and save the properties
            propsFileName.copyFrom(propsStream);

            // Copy the script to the project workspace
            CmdLineExe.copyFrom(cmdExeUrl);
        } catch (IOException e1) {
            build.setResult(Result.FAILURE);
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        try {
            // Run the HpToolsLauncher.exe
            AlmToolsUtils.runOnBuildEnv(build, launcher, listener, CmdLineExe, ParamFileName);
        } catch (IOException ioe) {
            Util.displayIOException(ioe, listener);
            build.setResult(Result.FAILURE);
            return;
        } catch (InterruptedException e) {
            build.setResult(Result.ABORTED);
            PrintStream out = listener.getLogger();
            
            try {
                AlmToolsUtils.runHpToolsAborterOnBuildEnv(build, launcher, listener, ParamFileName, workspace);
            } catch (IOException e1) {
                Util.displayIOException(e1, listener);
                build.setResult(Result.FAILURE);
                return;
    		} catch (InterruptedException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        }

        return;
    }

    public AlmServerSettingsModel getAlmServerSettingsModel() {
        if (runFromAlmModel != null) {
            return findAlmServerSettingsModel(getAlmServerName());
        }
        return null;
    }

    public RunFromAlmModel getRunFromAlmModel() {
        return runFromAlmModel;
    }

    public boolean isAreParametersEnabled() {
        return areParametersEnabled;
    }

    public void setAreParametersEnabled(boolean areParametersEnabled) {
        this.areParametersEnabled = areParametersEnabled;
    }

    public SpecifyParametersModel getSpecifyParametersModel() {
        return specifyParametersModel;
    }

    // This indicates to Jenkins that this is an implementation of an extension
    // point.
    @Extension
    // To expose this builder in the Snippet Generator.
    @Symbol("runFromAlmBuilder")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(
                @SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return RunFromAlmBuilderStepName(CompanyName());
        }

        public boolean hasAlmServers() {
            return AlmServerSettingsGlobalConfiguration.getInstance().hasAlmServers();
        }

        public Stream<AlmServerSettingsModel> getAlmServers() {
            return Arrays.stream(AlmServerSettingsGlobalConfiguration.getInstance().getInstallations()).sorted();
        }

        private AlmServerSettingsModel findAlmServer(String almServerName) {
            return StringUtils.isBlank(almServerName) ?
                    getAlmServers().findFirst().orElse(null) :
                    getAlmServers().filter(s -> s.getAlmServerName().equals(almServerName)).findFirst().orElse(null);
        }

        public ListBoxModel doFillAlmServerNameItems() {
            ListBoxModel m = new ListBoxModel();
            getAlmServers().forEachOrdered(s -> m.add(s.getAlmServerName()));
            return m;
        }

        public ListBoxModel doFillAlmUserNameItems(@QueryParameter String almServerName) {
            ListBoxModel m = new ListBoxModel();
            if (hasAlmServers()) {
                AlmServerSettingsModel model = findAlmServer(almServerName);
                if (model != null && !model.getAlmCredentials().isEmpty()) {
                    model.getAlmCredentials().forEach(cm -> m.add(cm.getAlmUsername()));
                } else if (StringUtils.isNotBlank(almServerName)) {
                    m.add(UftConstants.NO_USERNAME_DEFINED);
                }
            }

            return m;
        }

        public ListBoxModel doFillAlmClientIDItems(@QueryParameter String almServerName) {
            ListBoxModel m = new ListBoxModel();
            if (hasAlmServers()) {
                AlmServerSettingsModel model = findAlmServer(almServerName);
                if (model != null && !model.getAlmSSOCredentials().isEmpty()) {
                    model.getAlmSSOCredentials().forEach(cm -> m.add(cm.getAlmClientID()));
                } else {
                    m.add(UftConstants.NO_CLIENT_ID_DEFINED);
                }
            }
            return m;
        }

        public FormValidation doCheckAlmTimeout(@QueryParameter String value) {

            if (StringUtils.isEmpty(value)) {
                return FormValidation.ok();
            }

            String val1 = value.trim();

            if (val1.length() > 0 && val1.charAt(0) == '-')
                val1 = val1.substring(1);

            if (!StringUtils.isNumeric(val1) && !val1.equals("")) {
                return FormValidation.error("Timeout value must be a number");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckAlmDomain(@QueryParameter String value) {
            if (StringUtils.isBlank(value)) {
                return FormValidation.error("Domain must be set");
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckAlmProject(@QueryParameter String value) {
            if (StringUtils.isBlank(value)) {
                return FormValidation.error("Project must be set");
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckAlmTestSets(@QueryParameter String value) {
            if (StringUtils.isBlank(value)) {
                return FormValidation.error("Test sets are missing");
            }

            String[] testSetsArr = value.replaceAll("\r", "").split("\n");

            for (int i = 0; i < testSetsArr.length; i++) {
                if (StringUtils.isBlank(testSetsArr[i])) {
                    return FormValidation.error("Test sets should not contains empty lines");
                }
            }
            return FormValidation.ok();
        }

        public List<EnumDescription> getAlmRunModes() {
            return RunFromAlmModel.runModes;
        }

        public List<CredentialsScope> getAlmCredentialScopes() {
            return Arrays.asList(CredentialsScope.values());
        }
    }

    public String getRunResultsFileName() {
        return ResultFilename;
    }
}
