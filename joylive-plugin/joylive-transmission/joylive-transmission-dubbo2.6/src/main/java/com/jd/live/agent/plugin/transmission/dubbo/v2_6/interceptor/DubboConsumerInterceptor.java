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
package com.jd.live.agent.plugin.transmission.dubbo.v2_6.interceptor;

import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcInvocation;
import com.jd.live.agent.bootstrap.bytekit.context.ExecutableContext;
import com.jd.live.agent.core.plugin.definition.InterceptorAdaptor;
import com.jd.live.agent.governance.context.RequestContext;
import com.jd.live.agent.governance.context.bag.Carrier;
import com.jd.live.agent.governance.context.bag.Propagation;
import com.jd.live.agent.governance.request.HeaderReader.StringMapReader;
import com.jd.live.agent.plugin.transmission.dubbo.v2_6.request.RpcInvocationParser;

import static com.jd.live.agent.governance.context.bag.live.LivePropagation.LIVE_PROPAGATION;

public class DubboConsumerInterceptor extends InterceptorAdaptor {

    private final Propagation propagation;

    public DubboConsumerInterceptor(Propagation propagation) {
        this.propagation = propagation;
    }

    @Override
    public void onEnter(ExecutableContext ctx) {
        RpcInvocation invocation = ctx.getArgument(0);
        Carrier carrier = RequestContext.getOrCreate();
        // read from rpc context by live propagation
        LIVE_PROPAGATION.read(carrier, new StringMapReader(RpcContext.getContext().getAttachments()));
        // write to invocation with live attachments in rpc context
        propagation.write(carrier, new RpcInvocationParser(invocation));
    }

}
