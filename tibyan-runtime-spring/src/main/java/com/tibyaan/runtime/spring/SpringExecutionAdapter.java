package com.tibyaan.runtime.spring;

import com.tibyaan.plugin.CapabilityRef;
import com.tibyaan.plugin.ExecutionAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.joining;

/**
 * The Spring {@link ExecutionAdapter} (Volume 7). It is the only place that maps a capability's
 * opaque binding to a real call. Given a {@link CapabilityRef} and arguments, it looks up the
 * capability in the manifest, resolves the binding's {@code <Bean>#<method>} form to a real bean
 * and method through the {@link ApplicationContext}, and invokes it reflectively.
 *
 * <p>The binding is the real service method, discovered by the provider (Volume 5 section 4.1).
 * The adapter never depended on how the binding was produced: it resolves the same opaque
 * {@code <Bean>#<method>} string whether that string was once a convention or, as now, a
 * discovered fact, with no change here.
 */
public final class SpringExecutionAdapter implements ExecutionAdapter {

    private static final Logger log = LoggerFactory.getLogger(SpringExecutionAdapter.class);

    private final ApplicationContext applicationContext;
    private final Map<String, CapabilityDescriptor> capabilitiesByName;

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
                    + capability.name() + "'; expected <Bean>#<method>");
        }
        String beanHint = binding.substring(0, hash);
        String methodName = binding.substring(hash + 1);

        Object bean = resolveBean(beanHint);
        List<InputDescriptor> inputs = descriptor.inputs();

        // EI-R: resolve the method by name and ARITY, then convert each argument into that method's
        // DECLARED parameter type. Reconstructing the parameter types from the IR was the latent defect
        // (Volume 7 section 9): a STRING input yielded String.class against a real enum or LocalDate
        // parameter, so the lookup threw and the capability was uninvokable.
        Method method = findMethod(bean, methodName, inputs.size(), binding);
        Class<?>[] declaredTypes = method.getParameterTypes();
        Object[] callArgs = new Object[inputs.size()];
        for (int i = 0; i < inputs.size(); i++) {
            InputDescriptor input = inputs.get(i);
            callArgs[i] = IrTypes.coerce(safeArgs.get(input.name()), declaredTypes[i], input.name());
        }

        try {
            log.debug("Tibyaan: invoking {} for capability {}", binding, capability.name());
            return method.invoke(bean, callArgs);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("failed to invoke binding '" + binding + "'", e);
        }
    }

    // Resolve by bean name first (the Spring default, productService), then by type simple name.
    private Object resolveBean(String beanHint) {
        String beanName = Character.toLowerCase(beanHint.charAt(0)) + beanHint.substring(1);
        if (applicationContext.containsBean(beanName)) {
            return applicationContext.getBean(beanName);
        }
        for (String name : applicationContext.getBeanDefinitionNames()) {
            Class<?> type = applicationContext.getType(name);
            if (type != null && type.getSimpleName().equals(beanHint)) {
                return applicationContext.getBean(name);
            }
        }
        throw new IllegalStateException("no bean found for binding hint '" + beanHint + "'");
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
    private static Method findMethod(Object bean, String methodName, int arity, String binding) {
        List<Method> candidates = new ArrayList<>();
        for (Method method : bean.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == arity
                    && !method.isBridge() && !method.isSynthetic()) {
                candidates.add(method);
            }
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException("no method '" + methodName + "' taking " + arity
                    + " argument(s) on " + bean.getClass().getName() + " for binding '" + binding + "'");
        }
        if (candidates.size() > 1) {
            throw new IllegalStateException("binding '" + binding + "' is ambiguous: "
                    + candidates.size() + " overloads of '" + methodName + "' take " + arity
                    + " argument(s) on " + bean.getClass().getName() + " ("
                    + candidates.stream().map(Method::toGenericString).sorted().collect(joining("; "))
                    + "); the adapter never guesses which one a capability binds to");
        }
        return candidates.get(0);
    }
}
