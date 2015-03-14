package skadistats.clarity.two.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skadistats.clarity.two.framework.UsagePointProvider;
import skadistats.clarity.two.framework.UsagePoints;
import skadistats.clarity.two.framework.annotation.UsagePointMarker;
import skadistats.clarity.two.framework.invocation.*;
import skadistats.clarity.two.framework.invocation.EventListener;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;

public class Context {

    private static final Logger log = LoggerFactory.getLogger(Context.class);

    private Map<Class<?>, Object> processors = new HashMap<>();
    private Set<UsagePoint> usagePoints = new HashSet<>();
    private Map<Class<? extends Annotation>, Set<EventListener>> processedEvents = new HashMap<>();
    private Map<Class<? extends Annotation>, InitializerMethod> initializers = new HashMap<>();

    public void addProcessor(Object processor) {
        requireProcessorClass(processor.getClass());
        processors.put(processor.getClass(), processor);
    }

    public <T> T getProcessor(Class<T> processorClass) {
        return (T) processors.get(processorClass);
    }

    private void requireProcessorClass(Class<?> processorClass) {
        if (!processors.containsKey(processorClass)) {
            log.info("require processor {}", processorClass.getName());
            processors.put(processorClass, null);
            List<UsagePoint> ups = findUsagePoints(processorClass);
            for (UsagePoint up : ups) {
                usagePoints.add(up);
                if (up instanceof EventListener) {
                    requireEventListener((EventListener) up);
                } else if (up instanceof InitializerMethod) {
                    registerInitializer((InitializerMethod) up);
                }
            }
        }
    }

    private void requireEventListener(EventListener eventListener) {
        log.info("require event listener {}", eventListener.getEventClass());
        Set<EventListener> eventListeners = processedEvents.get(eventListener.getEventClass());
        if (eventListeners == null) {
            eventListeners = new HashSet<>();
            processedEvents.put(eventListener.getEventClass(), eventListeners);
        }
        eventListeners.add(eventListener);
        UsagePointProvider provider = UsagePoints.getProvidersFor(eventListener.getEventClass());
        if (provider == null) {
            throw new RuntimeException("oops. no provider found for required listener");
        }
        requireProcessorClass(provider.getProviderClass());
    }

    private void registerInitializer(InitializerMethod initializer) {
        log.info("register initializer {}", initializer.getEventClass());
        if (initializers.containsKey(initializer.getEventClass())) {
            log.warn("ignoring duplicate initializer for event {} found in {}, already provided by {}", initializer.getEventClass().getName(), initializer.getProcessorClass().getName(), initializers.get(initializer.getEventClass()).getProcessorClass().getName());
            return;
        }
        initializers.put(initializer.getEventClass(), initializer);
    }

    private List<UsagePoint> findUsagePoints(Class<?> searchedClass) {
        List<UsagePoint> ups = new ArrayList<>();
        for (Annotation classAnnotation : searchedClass.getAnnotations()) {
            if (classAnnotation.annotationType().isAnnotationPresent(UsagePointMarker.class)) {
                ups.add(UsagePointType.newInstance(classAnnotation, searchedClass, null));
            }
        }
        for (Method method : searchedClass.getMethods()) {
            for (Annotation methodAnnotation : method.getAnnotations()) {
                if (methodAnnotation.annotationType().isAnnotationPresent(UsagePointMarker.class)) {
                    ups.add(UsagePointType.newInstance(methodAnnotation, searchedClass, method));
                }
            }
        }
        return ups;
    }

    private void instantiateMissingProcessors() {
        for (Map.Entry<Class<?>, Object> entry : processors.entrySet()) {
            if (entry.getValue() == null) {
                try {
                    entry.setValue(entry.getKey().newInstance());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void bindInvocationPoints() {
        for (UsagePoint up : usagePoints) {
            if (up instanceof InvocationPoint){
                try {
                    ((InvocationPoint)up).bind(this);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }

            }
        }
    }

    private void callInitializers() {
        for (UsagePoint up : usagePoints) {
            InitializerMethod im = initializers.get(up.getAnnotation().annotationType());
            if (im != null){
                try {
                    im.invoke(up);
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public void initialize() {
        instantiateMissingProcessors();
        bindInvocationPoints();
        callInitializers();
    }

    public <A extends Annotation> Event<A> createEvent(Class<A> eventType, Class... parameterTypes) {
        Set<EventListener<A>> listeners = new HashSet<>();
        Set<EventListener> eventListeners = processedEvents.get(eventType);
        if(eventListeners != null) {
            for (EventListener<A> listener : eventListeners) {
                if (listener.isInvokedFor(parameterTypes)) {
                    listeners.add(listener);
                }
            }
        }
        return new Event<>(listeners);
    }

}
