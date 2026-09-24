package com.tibyaan.runtime.spring;

import com.tibyaan.plugin.CapabilityRef;
import com.tibyaan.plugin.ExecutionAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.stream.Collectors.joining;

/**
 * The Spring {@link ExecutionAdapter} (Volume 7). It is the only place that maps a capability's
 * opaque binding to a real call. Given a {@link CapabilityRef} and arguments, it looks up the
 * capability in the manifest, resolves the binding's {@code <qualified class>#<method>} form to a
 * real bean and method through the {@link ApplicationContext}, and invokes it reflectively.
 *
 * <p>The binding is the real service method, discovered by the provider (Volume 5 section 4.1).
 * Since phase Binding Identity (Volume 7 section 12, built 2026-09-24) its class part is the
 * canonical qualified name and the bean is resolved by CLASS IDENTITY, never by bean name and never
 * by simple name: a real host held a class of the same simple name as a reviewed target, and the
 * simple-name walk resolved to it (docs/experiments/as-backend-trial.md section 10.7, F7).
 */
public final class SpringExecutionAdapter implements ExecutionAdapter {

    private static final Logger log = LoggerFactory.getLogger(SpringExecutionAdapter.class);

    private final ApplicationContext applicationContext;
    private final Map<String, CapabilityDescriptor> capabilitiesByName;
    // R2 (section 12.2): a successful resolution is cached per class name; an observable difference
    // between resolving once and resolving on every call does not exist, since the context is fixed.
    private final Map<String, Object> beansByClassName = new ConcurrentHashMap<>();

    public SpringExecutionAdapter(ApplicationContext applicationContext, CapabilitiesManifest manifest) {
        this.applicationContext = applicationContext;
        Map<String, CapabilityDescriptor> byName = new LinkedHashMap<>();
        for (CapabilityDescriptor descriptor : manifest.tools()) {
            byName.put(descriptor.name(), descriptor);
        }
        this.capabilitiesByName = byName;
    }

    @Override
    public Object invoke(CapabilityRef capability, Map<String, Object> args) {
        CapabilityDescriptor descriptor = capabilitiesByName.get(capability.name());
        if (descriptor == null) {
            throw new IllegalArgumentException("unknown capability: " + capability.name());
        }
        Map<String, Object> safeArgs = args == null ? Map.of() : args;

        String binding = descriptor.binding();
        int hash = binding.indexOf('#');
        if (hash < 0) {
            throw new IllegalStateException("malformed binding '" + binding + "' for capability '"
                    + capability.name() + "'; expected <qualified class>#<method>");
        }
        String className = binding.substring(0, hash);
        String methodName = binding.substring(hash + 1);

        Object bean = resolveBean(className, binding, capability.name());
        List<InputDescriptor> inputs = descriptor.inputs();

        // EI-R: resolve the method by name and ARITY, then convert each argument into that method's
        // DECLARED parameter type. Reconstructing the parameter types from the IR was the latent defect
        // (Volume 7 section 9): a STRING input yielded String.class against a real enum or LocalDate
        // parameter, so the lookup threw and the capability was uninvokable.
        // The method is resolved on the USER class, never on the proxy subclass: a CGLIB override carries no
        // generic signature, so a List<Integer> parameter reads as a raw List there and a multiple input could
        // not be built (found on the first real collection-input call, as-backend-trial.md section 10.9).
        // Invoking the user-class Method on the proxy instance still dispatches virtually through the advice.
        Method method = findMethod(ClassUtils.getUserClass(bean.getClass()), methodName, inputs.size(), binding);
        Class<?>[] declaredTypes = method.getParameterTypes();
        java.lang.reflect.Type[] genericTypes = method.getGenericParameterTypes();
        Object[] callArgs = new Object[inputs.size()];
        for (int i = 0; i < inputs.size(); i++) {
            InputDescriptor input = inputs.get(i);
            // DL-2a (Volume 7 section 13 item 1): a multiple input arrives as an array and is built into the
            // declared collection, each element coerced by the EI-R rule to the generic element type.
            callArgs[i] = input.multiple()
                    ? IrTypes.coerceMany(safeArgs.get(input.name()), declaredTypes[i], genericTypes[i], input.name())
                    : IrTypes.coerce(safeArgs.get(input.name()), declaredTypes[i], input.name());
        }

        try {
            log.debug("Tibyaan: invoking {} for capability {}", binding, capability.name());
            return method.invoke(bean, callArgs);
        } catch (InvocationTargetException e) {
            // F8 (as-backend-trial.md section 10.7), a diagnostic amendment flagged under rule 13: the bound
            // method threw, and the caller gets the cause's class and message rather than an opaque
            // "failed to invoke". Nothing about the outcome changes; only what the failure says.
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.warn("Tibyaan: binding '{}' of capability '{}' threw {}: {}", binding, capability.name(),
                    cause.getClass().getName(), cause.getMessage());
            throw new IllegalStateException("failed to invoke binding '" + binding + "': "
                    + cause.getClass().getName() + (cause.getMessage() == null ? "" : ": " + cause.getMessage()),
                    cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("failed to invoke binding '" + binding + "'", e);
        }
    }

    /**
     * Resolves the binding's class part to exactly one bean whose USER class has that canonical name
     * (Volume 7 section 12.2, R2). The CGLIB subclass Spring Boot's default {@code proxyTargetClass=true}
     * produces for an advised bean is unwrapped by {@link ClassUtils#getUserClass(Class)}. Zero matches and
     * more than one match both refuse loudly; the adapter never picks a bean by its name or by its simple
     * class name, because a fallback to the simple name is the F7 defect itself.
     */
    private Object resolveBean(String className, String binding, String capabilityName) {
        if (className.indexOf('.') < 0) {
            // R3 (section 12.2): an unqualified class part was produced by a tool older than Binding Identity;
            // the loader refuses such a manifest at startup, and the adapter refuses it here as well so the
            // rule holds for a manifest handed to it by any other path.
            throw new IllegalStateException("binding '" + binding + "' of capability '" + capabilityName
                    + "' is not qualified (expected <qualified class>#<method>); the manifest was generated by"
                    + " a Tibyaan older than 0.4.0 and must be regenerated with the runtime's own version");
        }
        Object cached = beansByClassName.get(className);
        if (cached != null) {
            return cached;
        }
        List<String> matches = new ArrayList<>();
        for (String name : applicationContext.getBeanDefinitionNames()) {
            Class<?> type = applicationContext.getType(name, false);
            if (type != null && className.equals(ClassUtils.getUserClass(type).getCanonicalName())) {
                matches.add(name);
            }
        }
        if (matches.isEmpty()) {
            throw new IllegalStateException("no bean of class '" + className + "' is hosted by this application"
                    + " (binding '" + binding + "' of capability '" + capabilityName + "'); the bean is either not"
                    + " on this service's classpath, not registered here, or advised by a JDK interface proxy that"
                    + " hides its class");
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("binding '" + binding + "' of capability '" + capabilityName
                    + "' is ambiguous: beans " + matches + " are of class '" + className
                    + "'; the adapter never guesses which one a capability binds to");
        }
        Object bean = applicationContext.getBean(matches.get(0));
        beansByClassName.put(className, bean);
        return bean;
    }

    /**
     * Resolves the bound method by NAME and ARITY (Volume 7 section 9 item 1). Bridge and synthetic
     * methods are skipped, because a generic interface implementation declares both the real method and an
     * erased bridge of the same name and arity, and the bridge is not a distinct overload.
     *
     * <p>When more than one genuine overload matches, the adapter REFUSES LOUDLY and names the candidates.
     * It never guesses which overload a capability binds to: an invocation against the wrong overload is a
     * mis-bind at the moment of action, which is exactly what this project never does.
     */
    private static Method findMethod(Class<?> userClass, String methodName, int arity, String binding) {
        List<Method> candidates = new ArrayList<>();
        for (Method method : userClass.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == arity
                    && !method.isBridge() && !method.isSynthetic()) {
                candidates.add(method);
            }
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException("no method '" + methodName + "' taking " + arity
                    + " argument(s) on " + userClass.getName() + " for binding '" + binding + "'");
        }
        if (candidates.size() > 1) {
            throw new IllegalStateException("binding '" + binding + "' is ambiguous: "
                    + candidates.size() + " overloads of '" + methodName + "' take " + arity
                    + " argument(s) on " + userClass.getName() + " ("
                    + candidates.stream().map(Method::toGenericString).sorted().collect(joining("; "))
                    + "); the adapter never guesses which one a capability binds to");
        }
        return candidates.get(0);
    }
}
