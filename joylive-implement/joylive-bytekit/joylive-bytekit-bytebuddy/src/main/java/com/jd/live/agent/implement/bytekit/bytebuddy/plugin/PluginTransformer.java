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
package com.jd.live.agent.implement.bytekit.bytebuddy.plugin;

import com.jd.live.agent.bootstrap.bytekit.advice.AdviceDesc;
import com.jd.live.agent.bootstrap.bytekit.advice.AdviceHandler;
import com.jd.live.agent.bootstrap.bytekit.advice.AdviceKey;
import com.jd.live.agent.bootstrap.logger.Logger;
import com.jd.live.agent.bootstrap.logger.LoggerFactory;
import com.jd.live.agent.bootstrap.plugin.definition.Interceptor;
import com.jd.live.agent.core.plugin.definition.InterceptorDefinition;
import com.jd.live.agent.core.plugin.definition.PluginDeclare;
import com.jd.live.agent.core.plugin.definition.PluginDefinition;
import com.jd.live.agent.core.plugin.definition.PluginImporter;
import com.jd.live.agent.implement.bytekit.bytebuddy.advice.ConstructorAdvice;
import com.jd.live.agent.implement.bytekit.bytebuddy.advice.MemberMethodAdvice;
import com.jd.live.agent.implement.bytekit.bytebuddy.advice.StaticMethodAdvice;
import com.jd.live.agent.implement.bytekit.bytebuddy.type.BuddyMethodDesc;
import com.jd.live.agent.implement.bytekit.bytebuddy.type.BuddyTypeDesc;
import com.jd.live.agent.implement.bytekit.bytebuddy.util.ModuleUtil;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import net.bytebuddy.utility.nullability.MaybeNull;
import net.bytebuddy.utility.nullability.NeverNull;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A transformer that modifies the bytecode of classes loaded by the JVM, based on plugins that
 * declare the need to import internal classes or modify method behavior. It implements both the
 * {@link AgentBuilder.RawMatcher} for matching classes to be transformed and {@link AgentBuilder.Transformer}
 * for applying the transformations.
 *
 * @since 1.0.0
 */
public class PluginTransformer implements AgentBuilder.RawMatcher, AgentBuilder.Transformer {

    private static final Logger logger = LoggerFactory.getLogger(PluginTransformer.class);

    private final Instrumentation instrumentation;

    private final PluginDeclare plugin;

    private final Map<String, List<InterceptorDefinition>> types = new ConcurrentHashMap<>();

    /**
     * Constructs a new {@code PluginTransformer} with the specified instrumentation,
     * plugin declaration, and condition matcher.
     *
     * @param instrumentation  the {@link Instrumentation} object provided by the Java agent mechanism.
     * @param plugin           the plugin declaration that contains the definitions for class and method
     *                         transformations.
     */
    public PluginTransformer(Instrumentation instrumentation, PluginDeclare plugin) {
        this.instrumentation = instrumentation;
        this.plugin = plugin;
    }

    @Override
    public boolean matches(@NeverNull TypeDescription description,
                           @MaybeNull ClassLoader loader,
                           @MaybeNull JavaModule module,
                           @MaybeNull Class<?> type,
                           @MaybeNull ProtectionDomain domain) {
        if (plugin == null || plugin.isEmpty()) {
            return false;
        }

        String uniqueName = getUniqueName(description, loader);
        if (types.containsKey(uniqueName)) {
            return true;
        }

        BuddyTypeDesc typeDesc = new BuddyTypeDesc(description);
        List<InterceptorDefinition> interceptors = new ArrayList<>();
        List<PluginDefinition> definitions = plugin.match(typeDesc, loader);
        for (PluginDefinition definition : definitions) {
            interceptors.addAll(Arrays.asList(definition.getInterceptors()));
            // export internal java module packages to plugin
            export(loader, module, definition);
        }
        return !interceptors.isEmpty() && types.putIfAbsent(uniqueName, interceptors) == null;
    }

    /**
     * Exports or opens packages dynamically from one module to another based on the plugin definitions.
     * This method allows classes defined by plugins to access internal packages of other modules that
     * are not exported or opened by default.
     *
     * @param loader     The class loader associated with the module to be modified.
     * @param module     The module from which packages are to be exported or opened.
     * @param definition The plugin definition that may require access to internal packages.
     */
    private void export(ClassLoader loader, JavaModule module, PluginDefinition definition) {
        if (module == JavaModule.UNSUPPORTED || !(definition instanceof PluginImporter)) {
            return;
        }
        Map<String, Set<String>> targets = new HashMap<>();
        PluginImporter importer = (PluginImporter) definition;
        String[] imports = importer.getImports();
        if (imports != null && imports.length > 0) {
            targets.computeIfAbsent("", s -> new HashSet<>(Arrays.asList(imports)));
        }
        Map<String, String> importsTo = importer.getExports();
        if (importsTo != null) {
            for (Map.Entry<String, String> entry : importsTo.entrySet()) {
                targets.computeIfAbsent(entry.getValue(), s -> new HashSet<>()).add(entry.getKey());
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        JavaModule definitionModule = JavaModule.ofType(definition.getClass());
        ClassLoader candidate = this.getClass().getClassLoader();
        ModuleUtil.export(instrumentation, targets, definitionModule, loader, loader == candidate ? null : candidate);
    }

    /**
     * Generates a unique name for a type or class based on its description and class loader.
     * This unique name is used to identify classes across different class loaders.
     *
     * @param description The type description of the class.
     * @param loader      The class loader loading the class; may be {@code null} for the bootstrap class loader.
     * @return A unique name for the class, combining its full name and class loader.
     */
    private String getUniqueName(@NeverNull TypeDescription description, @MaybeNull ClassLoader loader) {
        return loader == null ? description.getActualName() : (description.getActualName() + "@" + loader);
    }

    @Override
    public DynamicType.Builder<?> transform(@NeverNull DynamicType.Builder<?> builder,
                                            @NeverNull TypeDescription description,
                                            @MaybeNull ClassLoader loader,
                                            @MaybeNull JavaModule module,
                                            @MaybeNull ProtectionDomain domain) {
        List<InterceptorDefinition> interceptorDefinitions = types.get(getUniqueName(description, loader));
        if (interceptorDefinitions == null || interceptorDefinitions.isEmpty()) {
            return builder;
        }

        DynamicType.Builder<?> newBuilder = builder;
        List<Interceptor> interceptors;
        for (MethodDescription.InDefinedShape methodDesc : description.getDeclaredMethods()) {
            if (methodDesc.isNative() || methodDesc.isAbstract()) {
                continue;
            }
            interceptors = getInterceptors(methodDesc, interceptorDefinitions);
            if (!interceptors.isEmpty()) {
                String adviceKey;
                BuddyMethodDesc desc = new BuddyMethodDesc(methodDesc);
                String key = desc.getDescription();
                try {
                    if (methodDesc.isStatic()) {
                        adviceKey = AdviceKey.getMethodKey(key, loader);
                        newBuilder = enhanceMethod(newBuilder, methodDesc, loader, interceptors, StaticMethodAdvice.class, adviceKey);
                    } else if (methodDesc.isConstructor()) {
                        adviceKey = AdviceKey.getMethodKey(key, loader);
                        newBuilder = enhanceMethod(newBuilder, methodDesc, loader, interceptors, ConstructorAdvice.class, adviceKey);
                    } else {
                        adviceKey = AdviceKey.getMethodKey(key, loader);
                        newBuilder = enhanceMethod(newBuilder, methodDesc, loader, interceptors, MemberMethodAdvice.class, adviceKey);
                    }
                } catch (NoSuchFieldException | NoSuchMethodException | IllegalAccessException |
                         InvocationTargetException e) {
                    logger.warn("failed to enhance " + key + " , caused by " + e.getMessage());
                }
            }
        }
        return newBuilder;
    }

    /**
     * Retrieves a list of interceptor instances applicable to a given method, based on a set of interceptor definitions.
     * Each interceptor definition includes a matcher that determines whether the interceptor should be applied to the method.
     *
     * @param methodDesc   The description of the method for which interceptors are being retrieved.
     * @param interceptors A list of interceptor definitions to evaluate against the method.
     * @return A list of {@link Interceptor} instances that match the method according to their respective definitions.
     */
    private List<Interceptor> getInterceptors(MethodDescription.InDefinedShape methodDesc, List<InterceptorDefinition> interceptors) {
        List<Interceptor> result = new ArrayList<>(interceptors.size());
        BuddyMethodDesc desc = new BuddyMethodDesc(methodDesc);
        for (InterceptorDefinition interceptor : interceptors) {
            if (!interceptor.getMatcher().match(desc)) {
                continue;
            }
            result.add(interceptor.getInterceptor());
        }
        return result;
    }

    /**
     * Enhances a method by applying a list of interceptors to it. This is achieved by using bytecode manipulation
     * to insert advice around the method execution. The advice is defined in a specified class and is selected
     * based on a unique key.
     *
     * @param builder      The builder used to create or modify the class that contains the method.
     * @param methodDesc   The description of the method to be enhanced.
     * @param classLoader  The class loader of the class being modified.
     * @param interceptors A list of interceptors to apply to the method.
     * @param templateCls  The class that contains the advice to be applied to the method.
     * @param adviceKey    A unique key identifying the specific advice to use.
     * @return A {@link DynamicType.Builder} instance representing the modified class.
     * @throws InvocationTargetException if an exception occurs while invoking the advice method.
     * @throws IllegalAccessException    if the advice method or field is not accessible.
     * @throws NoSuchMethodException     if the advice method does not exist.
     * @throws NoSuchFieldException      if a required field by the advice does not exist.
     */
    protected DynamicType.Builder<?> enhanceMethod(DynamicType.Builder<?> builder,
                                                   MethodDescription.InDefinedShape methodDesc,
                                                   ClassLoader classLoader,
                                                   List<Interceptor> interceptors,
                                                   Class<?> templateCls,
                                                   String adviceKey)
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException, NoSuchFieldException {
        AdviceDesc adviceDesc = AdviceHandler.getOrCreate(adviceKey);
        for (Interceptor interceptor : interceptors) {
            adviceDesc.add(interceptor);
        }
        if (adviceDesc.lock(plugin)) {
            return builder.visit(Advice.to(templateCls).on(ElementMatchers.is(methodDesc)));
        }
        return builder;
    }
}
