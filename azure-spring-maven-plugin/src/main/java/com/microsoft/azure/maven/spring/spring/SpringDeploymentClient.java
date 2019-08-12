/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.maven.spring.spring;

import com.microsoft.azure.management.microservices4spring.v2019_05_01_preview.ArtifactInfo;
import com.microsoft.azure.management.microservices4spring.v2019_05_01_preview.ArtifactResourceType;
import com.microsoft.azure.management.microservices4spring.v2019_05_01_preview.DeploymentResourceProperties;
import com.microsoft.azure.management.microservices4spring.v2019_05_01_preview.DeploymentResourceStatus;
import com.microsoft.azure.management.microservices4spring.v2019_05_01_preview.DeploymentSettings;
import com.microsoft.azure.management.microservices4spring.v2019_05_01_preview.PersistentDisk;
import com.microsoft.azure.management.microservices4spring.v2019_05_01_preview.RuntimeVersion;
import com.microsoft.azure.management.microservices4spring.v2019_05_01_preview.TemporaryDisk;
import com.microsoft.azure.management.microservices4spring.v2019_05_01_preview.implementation.DeploymentResourceInner;
import com.microsoft.azure.management.microservices4spring.v2019_05_01_preview.implementation.ResourceUploadDefinitionInner;
import com.microsoft.azure.maven.spring.configuration.Deployment;
import com.microsoft.azure.maven.spring.configuration.Volume;
import com.microsoft.azure.maven.spring.utils.Utils;
import org.apache.maven.plugin.MojoExecutionException;

public class SpringDeploymentClient extends AbstractSpringClient {
    protected String appName;
    protected String deploymentName;

    public static class Builder extends AbstractSpringClient.Builder<Builder> {
        protected String appName;
        protected String deploymentName;

        public Builder withAppName(String appName) {
            this.appName = appName;
            return self();
        }

        public Builder withDeploymentName(String deploymentName) {
            this.deploymentName = deploymentName;
            return self();
        }

        public SpringDeploymentClient build() {
            return new SpringDeploymentClient(this);
        }

        public Builder self() {
            return this;
        }
    }

    public DeploymentResourceInner createOrUpdateDeployment(
            Deployment deploymentConfiguration, ResourceUploadDefinitionInner resourceUploadDefinitionInner) throws MojoExecutionException {
        DeploymentResourceInner deployment = getDeployment();
        final DeploymentResourceProperties deploymentProperties = deployment == null ? new DeploymentResourceProperties() : deployment.properties();

        final DeploymentSettings deploymentSettings = deploymentProperties.deploymentSettings() == null ?
                new DeploymentSettings() : deploymentProperties.deploymentSettings();
        // Update persistent disk configuration
        final PersistentDisk persistentDisk = deploymentSettings.persistentDisk() == null ?
                new PersistentDisk() : deploymentSettings.persistentDisk();
        final Volume persistentDiskConfiguration = deploymentConfiguration.getPersistentDisk();
        if (persistentDiskConfiguration != null) {
            persistentDisk.withMountPath(persistentDiskConfiguration.getPath())
                    .withSizeInGB(Utils.convertSizeStringToNumber(persistentDiskConfiguration.getSize()));
        }
        // Update temporary disk configuration
        final TemporaryDisk temporaryDisk = deploymentSettings.temporaryDisk() == null ? new TemporaryDisk()
                : deploymentSettings.temporaryDisk();
        final Volume temporaryDiskConfiguration = deploymentConfiguration.getTemporaryDisk();
        if (temporaryDiskConfiguration != null) {
            temporaryDisk.withMountPath(temporaryDiskConfiguration.getPath())
                    .withSizeInGB(Utils.convertSizeStringToNumber(temporaryDiskConfiguration.getSize()));
        }

        deploymentSettings.withCpu(deploymentConfiguration.getCpu())
                .withInstanceCount(deploymentConfiguration.getInstanceCount())
                .withJvmOptions(deploymentConfiguration.getJvmParameter())
                .withMemoryInGB(deploymentConfiguration.getMemoryInGB())
                .withPersistentDisk(persistentDisk)
                .withTemporaryDisk(temporaryDisk)
                // Now we support java 8 only
                .withRuntimeVersion(RuntimeVersion.JAVA_8)
                .withEnvironmentVariables(deploymentConfiguration.getEnvironment());

        final ArtifactInfo artifactInfo = new ArtifactInfo();
        // There are some issues with server side resourceUpload logic
        // Use uploadUrl instead of relativePath
        final String relativePath = resourceUploadDefinitionInner.uploadUrl().split("\\?")[0];
        artifactInfo.withResourceType(ArtifactResourceType.JAR).withRelativePath(relativePath);
        deploymentProperties.withArtifact(artifactInfo).withDeploymentSettings(deploymentSettings);
        if (deployment == null) {
            deployment = springManager.deployments().inner().createOrUpdate(resourceGroup, clusterName, appName, deploymentName, deploymentProperties);
        } else {
            deployment = springManager.deployments().inner().update(resourceGroup, clusterName, appName, deploymentName, deploymentProperties);
        }
        springManager.deployments().inner().start(resourceGroup, clusterName, appName, deploymentName);
        return deployment;
    }

    public DeploymentResourceStatus getDeploymentStatus() {
        return getDeployment().properties().status();
    }

    public DeploymentResourceInner getDeployment() {
        return springManager.deployments().inner().get(resourceGroup, clusterName, appName, deploymentName);
    }

    public SpringDeploymentClient(Builder builder) {
        super(builder);
        this.appName = builder.appName;
        this.deploymentName = builder.deploymentName;
    }

    public SpringDeploymentClient(SpringAppClient springAppClient, String deploymentName) {
        super(springAppClient);
        this.appName = springAppClient.appName;
        this.deploymentName = deploymentName;
    }
}