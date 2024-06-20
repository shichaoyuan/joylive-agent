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
package com.jd.live.agent.plugin.router.kafka.v3.interceptor;

import com.jd.live.agent.bootstrap.bytekit.context.ExecutableContext;
import com.jd.live.agent.governance.interceptor.AbstractMQConsumerInterceptor;
import com.jd.live.agent.governance.invoke.InvocationContext;

import java.util.Map;
import java.util.Properties;

import static org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG;

public class ConsumerConfigInterceptor extends AbstractMQConsumerInterceptor {

    public ConsumerConfigInterceptor(InvocationContext context) {
        super(context);
    }

    @Override
    public void onEnter(ExecutableContext ctx) {
        Object[] arguments = ctx.getArguments();
        if (arguments[0] instanceof Properties) {
            configure((Properties) arguments[0]);
        } else if (arguments[0] instanceof Map) {
            configure((Map) arguments[0]);
        }
    }

    private void configure(Properties properties) {
        properties.put(GROUP_ID_CONFIG, getConsumerGroup(properties.getProperty(GROUP_ID_CONFIG)));
    }

    private void configure(Map map) {
        map.put(GROUP_ID_CONFIG, getConsumerGroup((String) map.get(GROUP_ID_CONFIG)));
    }

}
