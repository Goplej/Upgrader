package com.upgrader.util;

import com.upgrader.Upgrader;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Defensive reflection helpers for optional-dependency adapters.
 *
 * <p>All integration code paths run against classes that may be absent, renamed or obfuscated
 * differently between mod builds. Every lookup here swallows failures into {@link Optional#empty()}
 * and logs at DEBUG — callers decide how to degrade.</p>
 */
public final class ReflectionUtil {

    private ReflectionUtil() {
    }

    /** Loads a class by name, returning empty when unavailable. */
    public static Optional<Class<?>> classForName(String name) {
        try {
            return Optional.of(Class.forName(name, false, ReflectionUtil.class.getClassLoader()));
        } catch (Throwable t) {
            Upgrader.LOGGER.debug("Class {} unavailable: {}", name, t.toString());
            return Optional.empty();
        }
    }

    /** Finds a public static method by name + parameter types without triggering initialization errors. */
    public static Optional<Method> staticMethod(Class<?> owner, String name, Class<?>... params) {
        try {
            Method m = owner.getMethod(name, params);
            if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                return Optional.empty();
            }
            m.setAccessible(true);
            return Optional.of(m);
        } catch (Throwable t) {
            Upgrader.LOGGER.debug("Method {}#{}{} unavailable: {}", owner.getName(), name,
                    java.util.Arrays.toString(params), t.toString());
            return Optional.empty();
        }
    }

    /** Invokes {@code method}, mapping every failure mode to empty. */
    public static Optional<Object> invoke(Method method, Object target, Object... args) {
        try {
            return Optional.ofNullable(method.invoke(target, args));
        } catch (Throwable t) {
            Upgrader.LOGGER.debug("Invocation of {} failed: {}", method.getName(), t.toString());
            return Optional.empty();
        }
    }
}
