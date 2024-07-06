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
package com.jd.live.agent.governance.invoke;

import com.jd.live.agent.core.event.Publisher;
import com.jd.live.agent.core.instance.Location;
import com.jd.live.agent.core.util.matcher.Matcher;
import com.jd.live.agent.governance.event.TrafficEvent;
import com.jd.live.agent.governance.event.TrafficEvent.TrafficEventBuilder;
import com.jd.live.agent.governance.invoke.matcher.TagMatcher;
import com.jd.live.agent.governance.invoke.metadata.LaneMetadata;
import com.jd.live.agent.governance.invoke.metadata.LiveMetadata;
import com.jd.live.agent.governance.invoke.metadata.ServiceMetadata;
import com.jd.live.agent.governance.invoke.metadata.parser.LaneMetadataParser;
import com.jd.live.agent.governance.invoke.metadata.parser.MetadataParser;
import com.jd.live.agent.governance.invoke.metadata.parser.MetadataParser.LaneParser;
import com.jd.live.agent.governance.invoke.metadata.parser.MetadataParser.LiveParser;
import com.jd.live.agent.governance.invoke.metadata.parser.MetadataParser.ServiceParser;
import com.jd.live.agent.governance.policy.AccessMode;
import com.jd.live.agent.governance.policy.GovernancePolicy;
import com.jd.live.agent.governance.policy.PolicyId;
import com.jd.live.agent.governance.policy.lane.Lane;
import com.jd.live.agent.governance.policy.lane.LaneSpace;
import com.jd.live.agent.governance.policy.live.*;
import com.jd.live.agent.governance.policy.service.circuitbreaker.DegradeConfig;
import com.jd.live.agent.governance.request.ServiceRequest;
import com.jd.live.agent.governance.response.ServiceResponse;
import com.jd.live.agent.governance.rule.tag.TagCondition;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Abstract class for an invocation, encapsulating the context and metadata required for processing a service request.
 * <p>
 * This class provides a common foundation for handling various aspects of a service request, such as governance policies,
 * unit functions, variable functions, and service configuration. It also offers utility methods for matching tags,
 * accessing governance configurations, and dealing with faults and failovers.
 *
 * @param <T> the type of service request this invocation is handling
 */
public abstract class Invocation<T extends ServiceRequest> implements RequestListener, Matcher<TagCondition> {

    public static final String FAILOVER_UNIT_NOT_ACCESSIBLE = "failover when unit is not accessible.";
    public static final String REJECT_NO_UNIT = "reject when local unit is not found.";
    public static final String REJECT_UNIT_NOT_CENTER = "reject when unit is not center.";
    public static final String REJECT_NO_CENTER = "reject when center unit is not found.";
    public static final String REJECT_UNIT_NOT_ACCESSIBLE = "reject when unit is not accessible.";
    public static final String REJECT_NO_VARIABLE = "reject when unit variable is not found.";
    public static final String REJECT_NO_UNIT_ROUTE = "reject when unit route is not found.";
    public static final String REJECT_NO_INSTANCE = "reject when instance is not found.";
    public static final String FAILOVER_CENTER_NO_VARIABLE = "failover center unit when unit variable is not found.";
    public static final String FAILOVER_ESCAPE = "failover unit when variable is not belong to this unit.";
    public static final String FAILOVER_CELL_NOT_ACCESSIBLE = "failover other cell when local cell is not accessible.";

    /**
     * The service request associated with this invocation.
     */
    @Getter
    protected T request;

    @Getter
    protected InvocationContext context;

    @Getter
    protected GovernancePolicy governancePolicy;

    @Getter
    protected ServiceMetadata serviceMetadata;

    @Getter
    protected LiveMetadata liveMetadata;

    @Getter
    protected LaneMetadata laneMetadata;

    protected List<RequestListener> listeners;

    /**
     * The policy id for this invocation
     */
    protected PolicyId policyId;

    /**
     * Constructs a new Invocation object.
     */
    protected Invocation() {
    }

    /**
     * Constructs a new Invocation object with a specific request and invocation context.
     *
     * @param request the service request
     * @param context the invocation context
     */
    public Invocation(T request, InvocationContext context) {
        this.request = request;
        this.context = context;
        this.governancePolicy = context.getPolicySupplier().getPolicy();
        parsePolicy();
    }

    /**
     * Parses and configures the policy metadata.
     * <p>
     * This method creates instances of {@link ServiceParser}, {@link LiveParser}, and {@link MetadataParser}
     * for parsing and configuring the service metadata, live metadata, and lane metadata, respectively.
     * The parsed and configured metadata is stored in the corresponding instance variables of the class.
     * </p>
     */
    protected void parsePolicy() {
        ServiceParser serviceParser = createServiceParser();
        LiveParser liveParser = createLiveParser();
        MetadataParser<LaneMetadata> laneParser = createLaneParser();
        ServiceMetadata serviceMetadata = serviceParser.parse();
        LiveMetadata liveMetadata = liveParser.parse();
        this.serviceMetadata = serviceParser.configure(serviceMetadata, liveMetadata.getUnitRule());
        this.liveMetadata = liveParser.configure(liveMetadata, serviceMetadata.getServicePolicy());
        this.laneMetadata = laneParser.parse();
        this.policyId = parsePolicyId();
    }

    /**
     * Creates an instance of {@link LiveParser} for parsing live metadata.
     *
     * @return the created {@link LiveParser} instance
     */
    protected abstract LiveParser createLiveParser();

    /**
     * Creates an instance of {@link ServiceParser} for parsing service metadata.
     *
     * @return the created {@link ServiceParser} instance
     */
    protected abstract ServiceParser createServiceParser();

    /**
     * Creates an instance of {@link LaneParser} for parsing lane metadata.
     *
     * @return the created {@link LaneParser} instance
     */
    protected LaneParser createLaneParser() {
        return new LaneMetadataParser(request, context.getGovernanceConfig().getLaneConfig(),
                context.getApplication(), governancePolicy);
    }

    /**
     * Parses the policy ID from the service metadata.
     *
     * @return the parsed policy ID
     */
    protected PolicyId parsePolicyId() {
        return serviceMetadata.getServicePolicy();
    }

    /**
     * Determines if a place is accessible based on the current request's write status and the place's access mode.
     *
     * @param place The place to check for accessibility.
     * @return true if the place is accessible, false otherwise.
     */
    public boolean isAccessible(Place place) {
        if (place != null) {
            AccessMode accessMode = place.getAccessMode();
            accessMode = accessMode == null ? AccessMode.READ_WRITE : accessMode;
            switch (accessMode) {
                case READ_WRITE:
                    return true;
                case READ:
                    return !serviceMetadata.isWrite();
            }
        }
        return false;
    }

    /**
     * Matches a tag condition against the request.
     *
     * @param condition The tag condition to match.
     * @return true if the condition matches the request, false otherwise.
     */
    public boolean match(TagCondition condition) {
        if (condition == null) {
            return true;
        } else if (request == null) {
            return false;
        }
        Map<String, TagMatcher> matchers = context.getTagMatchers();
        TagMatcher matcher = matchers == null ? null : matchers.get(condition.getType().toLowerCase());
        return matcher != null && matcher.match(condition, request);
    }

    /**
     * Rejects the request with a specified fault type and reason.
     *
     * @param type   The type of fault.
     * @param reason The reason for the fault.
     */
    public void reject(FaultType type, String reason) {
        request.reject(type, reason);
    }

    /**
     * Initiates a failover for the request with a specified fault type and reason.
     *
     * @param type   The type of fault.
     * @param reason The reason for the failover.
     */
    public void failover(FaultType type, String reason) {
        request.failover(type, reason);
    }

    /**
     * Initiates a degradation for the request with a specified fault type and reason.
     *
     * @param type   The type of fault.
     * @param reason The reason for the failover.
     * @param config The degrade config.
     */
    public void degrade(FaultType type, String reason, DegradeConfig config) {
        request.degrade(type, reason, config);
    }

    /**
     * Adds a {@link RequestListener} to the list of listeners.
     *
     * @param listener the {@link RequestListener} to add, if it is not null
     */
    public void addListener(RequestListener listener) {
        if (listener != null) {
            if (listeners == null) {
                listeners = new ArrayList<>();
            }
            listeners.add(listener);
        }
    }

    @Override
    public void onSuccess(ServiceRequest request, ServiceResponse response) {
        // TODO publish event in this method, include traffic event
        if (listeners != null) {
            listeners.forEach(listener -> listener.onSuccess(request, response));
        }
    }

    @Override
    public void onFailure(ServiceRequest request, Throwable throwable) {
        // TODO publish event in this method, include traffic event
        if (listeners != null) {
            listeners.forEach(listener -> listener.onFailure(request, throwable));
        }
    }

    /**
     * Publishes a live event to a specified publisher using a configured live event builder.
     *
     * @param publisher The publisher to which the live event will be offered.
     * @param builder   The live event builder used to configure and build the live event.
     */
    public void publish(Publisher<TrafficEvent> publisher, TrafficEventBuilder builder) {
        if (publisher != null && builder != null) {
            TrafficEvent event = configure(builder).build();
            if (event != null) {
                publisher.tryOffer(event);
            }
        }
    }

    /**
     * Configures a live event builder with details from the current invocation context.
     *
     * @param builder The live event builder to configure.
     * @return The configured live event builder.
     */
    protected TrafficEventBuilder configure(TrafficEventBuilder builder) {
        LiveSpace liveSpace = liveMetadata.getLiveSpace();
        UnitRule unitRule = liveMetadata.getUnitRule();
        Unit currentUnit = liveMetadata.getCurrentUnit();
        Cell currentCell = liveMetadata.getCurrentCell();
        LaneSpace laneSpace = laneMetadata.getLaneSpace();
        Lane currentLane = laneMetadata.getCurrentLane();
        Lane targetLane = laneMetadata.getTargetLane();
        return builder.liveSpaceId(liveSpace == null ? null : liveSpace.getId()).
                unitRuleId(unitRule == null ? null : unitRule.getId()).
                localUnit(currentUnit == null ? null : currentUnit.getCode()).
                localCell(currentCell == null ? null : currentCell.getCode()).
                laneSpaceId(laneSpace == null ? null : laneSpace.getId()).
                localLane(currentLane == null ? null : currentLane.getCode()).
                targetLane(targetLane == null ? null : targetLane.getCode()).
                policyId(policyId == null ? null : policyId.getId()).
                service(policyId == null ? null : policyId.getTag(PolicyId.KEY_SERVICE_NAME)).
                group(policyId == null ? null : policyId.getTag(PolicyId.KEY_SERVICE_GROUP)).
                path(policyId == null ? null : policyId.getTag(PolicyId.KEY_SERVICE_PATH)).
                method(policyId == null ? null : policyId.getTag(PolicyId.KEY_SERVICE_METHOD));
    }

    /**
     * Constructs an error message incorporating additional details from the invocation context.
     * This method overloads {@link #getError(String, String, String)} by using the application's location.
     *
     * @param message The base error message.
     * @return The constructed error message with additional context details.
     */
    public String getError(String message) {
        return getError(message, context.getApplication().getLocation());
    }

    /**
     * Constructs an error message incorporating additional details from the specified location.
     * This method overloads {@link #getError(String, String, String)} by extracting unit and cell from the given location.
     *
     * @param message  The base error message.
     * @param location The location to include in the error message.
     * @return The constructed error message with additional context details.
     */
    public String getError(String message, Location location) {
        return getError(message, location.getUnit(), location.getCell());
    }

    /**
     * Constructs an error message incorporating additional details using the specified unit.
     * This method overloads {@link #getError(String, String, String)} by allowing a null cell value.
     *
     * @param message The base error message.
     * @param unit    The unit to include in the error message.
     * @return The constructed error message with additional context details.
     */
    public String getError(String message, String unit) {
        return getError(message, unit, null);
    }

    /**
     * Constructs an error message incorporating detailed context information from the invocation.
     * This method appends various details such as live space ID, rule ID, unit, cell, application name,
     * service name, service group, request path, and variable to the base error message.
     *
     * @param message The base error message.
     * @param unit    The unit associated with the error, may be null.
     * @param cell    The cell associated with the error, may be null.
     * @return The constructed error message with detailed context information.
     */
    public String getError(String message, String unit, String cell) {
        LiveSpace liveSpace = liveMetadata.getLiveSpace();
        return new StringBuilder(message.length() + 150).append(message).
                append(". liveSpaceId=").append(liveSpace == null ? null : liveSpace.getId()).
                append(", ruleId=").append(liveMetadata.getUnitRuleId()).
                append(", unit=").append(unit).
                append(", cell=").append(cell).
                append(", application=").append(context.getApplication().getName()).
                append(", service=").append(serviceMetadata.getServiceName()).
                append(", group=").append(serviceMetadata.getServiceGroup()).
                append(", path=").append(serviceMetadata.getPath()).
                append(", variable=").append(liveMetadata.getVariable()).append("\n").toString();
    }
}
