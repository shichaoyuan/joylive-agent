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
package com.jd.live.agent.governance.invoke.circuitbreak;

import com.jd.live.agent.core.util.URI;
import com.jd.live.agent.governance.policy.service.circuitbreak.CircuitBreakPolicy;

/**
 * CircuitBreaker interface defines the contract for a circuit breaker implementation.
 *
 * @since 1.1.0
 */
public interface CircuitBreaker {

    /**
     * Attempts to acquire a permit and returns the result.
     *
     * @return true if the permit is acquired successfully, false otherwise.
     */
    default boolean acquire() {
        return true;
    }

    /**
     * Retrieves the timestamp of the last successful acquisition.
     *
     * @return the timestamp of the last acquisition in milliseconds.
     */
    long getLastAcquireTime();

    /**
     * Checks if the circuit breaker is currently open.
     *
     * @return true if the circuit breaker is open, false otherwise.
     */
    boolean isOpen();

    /**
     * Releases the acquired permit.
     */
    default void release() {
        // do nothing
    }

    /**
     * Records a failed call. This method should be invoked when a call fails.
     *
     * @param durationInMs The elapsed time duration of the call in milliseconds.
     * @param throwable    The throwable that represents the failure.
     */
    void onError(long durationInMs, Throwable throwable);

    /**
     * Records a successful call. This method should be invoked when a call is successful.
     *
     * @param durationInMs The elapsed time duration of the call in milliseconds.
     */
    void onSuccess(long durationInMs);

    /**
     * Registers a listener to watch for state change events.
     *
     * @param listener The state change listener to register.
     */
    void addListener(CircuitBreakerStateListener listener);

    /**
     * Retrieves the policy that governs the behavior of the circuit breaker.
     *
     * @return the circuit breaker policy.
     */
    CircuitBreakPolicy getPolicy();

    /**
     * Obtains the URI related to the circuit breaker.
     *
     * @return the URI (Uniform Resource Identifier) associated with the circuit breaker.
     */
    URI getUri();
}
