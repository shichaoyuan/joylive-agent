/*
 * Copyright © ${year} ${owner} (${email})
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jd.live.agent.plugin.transmission.thread.definition;

import com.jd.live.agent.core.bytekit.matcher.MatcherBuilder;
import com.jd.live.agent.core.extension.annotation.ConditionalOnProperty;
import com.jd.live.agent.core.extension.annotation.Extension;
import com.jd.live.agent.core.inject.annotation.Inject;
import com.jd.live.agent.core.inject.annotation.Injectable;
import com.jd.live.agent.core.plugin.definition.*;
import com.jd.live.agent.core.thread.Camera;
import com.jd.live.agent.governance.annotation.ConditionalOnTransmissionEnabled;
import com.jd.live.agent.governance.config.GovernanceConfig;
import com.jd.live.agent.plugin.transmission.thread.interceptor.ExecutorInterceptor;

import java.util.List;

/**
 * ExecutorDefinition
 */
@Injectable
@Extension(value = "ExecutorDefinition", order = PluginDefinition.ORDER_TRANSMISSION)
@ConditionalOnTransmissionEnabled
@ConditionalOnProperty(value = GovernanceConfig.CONFIG_TRANSMISSION_THREADPOOL_ENABLED)
public class ExecutorDefinition extends PluginDefinitionAdapter implements PluginImporter {
    private static final String TYPE_EXECUTOR = "java.util.concurrent.Executor";

    private static final String METHOD_EXECUTE = "execute";

    private static final String METHOD_SUBMIT = "submit";

    private static final String[] METHODS = {METHOD_EXECUTE, METHOD_SUBMIT};

    @Inject
    private List<Camera> handlers;

    @Inject(GovernanceConfig.COMPONENT_GOVERNANCE_CONFIG)
    private GovernanceConfig governanceConfig;

    public ExecutorDefinition() {
        this.matcher = () -> MatcherBuilder.isImplement(TYPE_EXECUTOR).
                and(MatcherBuilder.not(MatcherBuilder.in(governanceConfig.getTransmitConfig().getThreadConfig().getExcludeExecutors())));
        this.interceptors = new InterceptorDefinition[]{
                new InterceptorDefinitionAdapter(MatcherBuilder.in(METHODS).and(MatcherBuilder.isPublic()),
                        () -> new ExecutorInterceptor(handlers, governanceConfig.getTransmitConfig().getThreadConfig()))};
    }

    @Override
    public String[] getImports() {
        return new String[]{"java.util.concurrent.FutureTask"};
    }
}