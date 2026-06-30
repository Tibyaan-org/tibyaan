package com.tibyaan.runtime.spring;

import com.tibyaan.plugin.CapabilityRef;
import com.tibyaan.plugin.ExecutionAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Spring {@link ExecutionAdapter} (Volume 7). It is the only place that maps a capability's
 * opaque binding to a real call. Given a {@link CapabilityRef} and arguments, it looks up the
 * capability in the manifest, resolves the binding convention {@code <Bean>#<method>} to a real
 * bean and method through the {@link ApplicationContext}, and invokes it reflectively.
 *
 * <p>The binding is a convention today (Volume 5 section 4.1); the adapter resolves it, it does
 * not depend on the convention being permanent. When the ServiceExtractor later supplies a real
 * method binding, this adapter resolves that instead, with no change here.
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
        Class<?>[] parameterTypes = new Class<?>[inputs.size()];
        Object[] callArgs = new Object[inputs.size()];
        for (int i = 0; i < inputs.size(); i++) {
            InputDescriptor input = inputs.get(i);
            parameterTypes[i] = IrTypes.toJavaClass(input.type());
            callArgs[i] = IrTypes.coerce(safeArgs.get(input.name()), parameterTypes[i]);
        }

        Method method = findMethod(bean, methodName, parameterTypes);
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

    private static Method findMethod(Object bean, String methodName, Class<?>[] parameterTypes) {
        try {
            return bean.getClass().getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("no method '" + methodName + "' on "
                    + bean.getClass().getName(), e);
        }
    }
}
