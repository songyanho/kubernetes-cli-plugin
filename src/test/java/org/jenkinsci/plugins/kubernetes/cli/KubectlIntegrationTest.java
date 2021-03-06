package org.jenkinsci.plugins.kubernetes.cli;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.FilePath;
import org.jenkinsci.plugins.kubernetes.cli.helpers.DummyCredentials;
import org.jenkinsci.plugins.kubernetes.cli.helpers.TestResourceLoader;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * @author Max Laverse
 */
public class KubectlIntegrationTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    protected static final String CREDENTIAL_ID = "test-credentials";
    protected static final String SECONDARY_CREDENTIAL_ID = "cred9999";
    protected static final String KUBECTL_BINARY = "kubectl";

    protected boolean kubectlPresent() {
        return Stream.of(System.getenv("PATH").split(Pattern.quote(File.pathSeparator)))
                .map(Paths::get)
                .map(p -> p.resolve(KUBECTL_BINARY))
                .filter(Files::exists)
                .anyMatch(Files::isExecutable);
    }

    @Before
    public void checkKubectlPresence() {
        assumeTrue("The 'kubectl' binary could not be found in the PATH",kubectlPresent());
    }

    @Test
    public void testSingleKubeConfig() throws Exception {
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), DummyCredentials.usernamePasswordCredential(CREDENTIAL_ID));

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "testBasicWithCa");
        p.setDefinition(new CpsFlowDefinition(TestResourceLoader.loadAsString("withKubeConfigPipelineConfigDump.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));

        r.assertLogContains("kubectl configuration cleaned up", b);
        FilePath configDump = r.jenkins.getWorkspaceFor(p).child("configDump");
        assertTrue(configDump.exists());
        String configDumpContent = configDump.readToString().trim();

        assertThat(configDumpContent, containsString("apiVersion: v1\n" +
                "clusters:\n" +
                "- cluster:\n" +
                "    insecure-skip-tls-verify: true\n" +
                "    server: https://localhost:6443\n" +
                "  name: k8s\n" +
                "contexts:\n" +
                "- context:\n" +
                "    cluster: k8s\n" +
                "    user: test-credentials\n" +
                "  name: k8s\n" +
                "current-context: k8s\n" +
                "kind: Config\n" +
                "preferences: {}\n" +
                "users:\n" +
                "- name: test-credentials\n" +
                "  user:\n" +
                "    password: s3cr3t\n" +
                "    username: bob"));
    }

    @Test
    public void testMultiKubeConfig() throws Exception {
        CredentialsStore store = CredentialsProvider.lookupStores(r.jenkins).iterator().next();
        store.addCredentials(Domain.global(), DummyCredentials.fileCredential(CREDENTIAL_ID));
        store.addCredentials(Domain.global(), DummyCredentials.fileCredential(SECONDARY_CREDENTIAL_ID,"test-cluster2","test-user2"));

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "multiKubeConfig");
        p.setDefinition(new CpsFlowDefinition(TestResourceLoader.loadAsString("withKubeCredentialsPipelineConfigDump.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));

        FilePath configDump = r.jenkins.getWorkspaceFor(p).child("configDump");
        assertTrue(configDump.exists());
        String configDumpContent = configDump.readToString().trim();

        assertThat(configDumpContent, containsString("apiVersion: v1\n" +
                "clusters:\n" +
                "- cluster:\n" +
                "    insecure-skip-tls-verify: true\n"+
                "    server: https://test-cluster\n" +
                "  name: test-cluster\n" +
                "- cluster:\n" +
                "    insecure-skip-tls-verify: true\n"+
                "    server: https://test-cluster2\n" +
                "  name: test-cluster2\n" +
                "contexts:\n" +
                "- context:\n" +
                "    cluster: test-cluster\n" +
                "    user: test-user\n" +
                "  name: test-cluster\n" +
                "- context:\n" +
                "    cluster: test-cluster2\n" +
                "    user: test-user2\n" +
                "  name: test-cluster2\n" +
                "current-context: test-cluster\n"+
                "kind: Config\n" +
                "preferences: {}\n" +
                "users:\n" +
                "- name: test-user\n" +
                "  user: {}\n" +
                "- name: test-user2\n" +
                "  user: {}"));
    }

    @Test
    public void testMultiKubeConfigUsernames() throws Exception {
        CredentialsStore store = CredentialsProvider.lookupStores(r.jenkins).iterator().next();
        store.addCredentials(Domain.global(), DummyCredentials.secretCredential(CREDENTIAL_ID));
        store.addCredentials(Domain.global(), DummyCredentials.fileCredential(SECONDARY_CREDENTIAL_ID,"test-cluster2","test-user2"));

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "multiKubeConfigUsernames");
        p.setDefinition(new CpsFlowDefinition(TestResourceLoader.loadAsString("withKubeCredentialsPipelineAndUsernames.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));

        FilePath configDump = r.jenkins.getWorkspaceFor(p).child("configDump");
        assertTrue(configDump.exists());
        String configDumpContent = configDump.readToString().trim();

        assertEquals("apiVersion: v1\n" +
                "clusters:\n" +
                "- cluster:\n" +
                "    insecure-skip-tls-verify: true\n" +
                "    server: https://localhost:1234\n" +
                "  name: clus1234\n" +
                "- cluster:\n" +
                "    insecure-skip-tls-verify: true\n" +
                "    server: https://localhost:9999\n" +
                "  name: clus9999\n" +
                "- cluster:\n" +
                "    insecure-skip-tls-verify: true\n" +
                "    server: https://test-cluster2\n" +
                "  name: test-cluster2\n"+
                "contexts:\n" +
                "- context:\n" +
                "    cluster: clus1234\n" +
                "    user: test-credentials\n" +
                "  name: cont1234\n" +
                "- context:\n" +
                "    cluster: clus9999\n" +
                "    user: test-user2\n" +
                "  name: test-cluster2\n" +
                "current-context: test-cluster2\n" +
                "kind: Config\n" +
                "preferences: {}\n" +
                "users:\n" +
                "- name: test-credentials\n" +
                "  user:\n" +
                "    token: s3cr3t\n" +
                "- name: test-user2\n" +
                "  user: {}", configDumpContent);
    }

    @Test
    public void testMultiKubeConfigWithServer() throws Exception {
        CredentialsStore store = CredentialsProvider.lookupStores(r.jenkins).iterator().next();
        store.addCredentials(Domain.global(), DummyCredentials.fileCredential(CREDENTIAL_ID));
        store.addCredentials(Domain.global(), DummyCredentials.fileCredential(SECONDARY_CREDENTIAL_ID,"test-cluster2","test-user2"));

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "multiKubeConfigWithServer");
        p.setDefinition(new CpsFlowDefinition(TestResourceLoader.loadAsString("withKubeCredentialsPipelineAndServer.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));

        FilePath configDump = r.jenkins.getWorkspaceFor(p).child("configDump");
        assertTrue(configDump.exists());
        String configDumpContent = configDump.readToString().trim();

        assertThat(configDumpContent, containsString("apiVersion: v1\n" +
                "clusters:\n" +
                "- cluster:\n" +
                "    insecure-skip-tls-verify: true\n" +
                "    server: https://localhost:9999\n" +
                "  name: cred9999\n" +
                "- cluster:\n" +
                "    insecure-skip-tls-verify: true\n" +
                "    server: https://test-cluster\n" +
                "  name: test-cluster\n" +
                "- cluster:\n" +
                "    insecure-skip-tls-verify: true\n"+
                "    server: https://test-cluster2\n" +
                "  name: test-cluster2\n" +
                "contexts:\n" +
                "- context:\n" +
                "    cluster: test-cluster\n" +
                "    user: test-user\n" +
                "  name: test-cluster\n" +
                "- context:\n" +
                "    cluster: cred9999\n" +
                "    user: test-user2\n" +
                "  name: test-cluster2\n" +
                "current-context: test-cluster\n"+
                "kind: Config\n" +
                "preferences: {}\n" +
                "users:\n" +
                "- name: test-user\n" +
                "  user: {}\n" +
                "- name: test-user2\n" +
                "  user: {}"));
    }
}
