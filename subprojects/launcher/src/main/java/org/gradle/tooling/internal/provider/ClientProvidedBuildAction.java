/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.tooling.internal.provider;

import org.gradle.api.internal.GradleInternal;
import org.gradle.initialization.BuildAction;
import org.gradle.initialization.BuildController;
import org.gradle.initialization.DefaultGradleLauncher;
import org.gradle.initialization.ModelConfigurationListener;
import org.gradle.tooling.internal.protocol.*;
import org.gradle.tooling.internal.provider.connection.ProviderBuildResult;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicReference;

class ClientProvidedBuildAction implements BuildAction<SerializedPayload>, Serializable {
    private final SerializedPayload action;

    public ClientProvidedBuildAction(SerializedPayload action) {
        this.action = action;
    }

    public SerializedPayload run(final BuildController buildController) {
        final DefaultGradleLauncher gradleLauncher = (DefaultGradleLauncher) buildController.getLauncher();
        PayloadSerializer payloadSerializer = gradleLauncher.getGradle().getServices().get(PayloadSerializer.class);
        final InternalBuildAction<?> action = (InternalBuildAction<?>) payloadSerializer.deserialize(this.action);
        final AtomicReference<Object> result = new AtomicReference<Object>();

        gradleLauncher.addListener(new ModelConfigurationListener() {
            public void onConfigure(final GradleInternal gradle) {
                InternalBuildController internalBuildController = new InternalBuildController() {
                    public BuildResult<?> getBuildModel() throws BuildExceptionVersion1 {
                        return new ProviderBuildResult<Object>(gradle);
                    }

                    public BuildResult<?> getModel(final ModelIdentifier modelIdentifier) throws BuildExceptionVersion1, InternalUnsupportedModelException {
                        ToolingModelBuilder builder = gradle.getDefaultProject().getServices().get(ToolingModelBuilderRegistry.class).getBuilder(modelIdentifier.getName());
                        Object model = builder.buildAll(modelIdentifier.getName(), gradle.getDefaultProject());
                        return new ProviderBuildResult<Object>(model);
                    }
                };
                result.set(action.execute(internalBuildController));
            }
        });

        buildController.configure();
        return payloadSerializer.serialize(result.get());
    }
}
