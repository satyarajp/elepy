package com.elepy.di;

import com.elepy.annotations.RestModel;
import com.elepy.dao.Crud;
import com.elepy.exceptions.ElepyConfigException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

public class DefaultElepyContext implements ElepyContext {

    private final Map<ContextKey, Object> contextMap;

    public DefaultElepyContext() {
        this.contextMap = new HashMap<>();
    }

    public <T> void attachSingleton(Class<T> cls, T object) {
        attachSingleton(cls, null, object);
    }

    public <T> void attachSingleton(T object) {
        attachSingleton(object, null);
    }

    public <T> void attachSingleton(T object, String tag) {
        contextMap.put(new ContextKey(object.getClass(), tag), object);
    }

    public <T> void attachSingleton(Class<T> cls, String tag, T object) {
        contextMap.put(new ContextKey<>(cls, tag), object);
    }

    public <T> T getSingleton(Class<T> cls) {
        return getSingleton(cls, null);
    }

    public <T> T getSingleton(Class<T> cls, String tag) {
        final ContextKey<T> key = new ContextKey<>(cls, tag);

        final T t = (T) contextMap.get(key);
        if (t != null) {
            return t;
        }

        throw new ElepyConfigException(String.format("No singleton for %s available", cls.getName()));
    }


    public <T> Crud<T> getCrudFor(Class<T> cls) {
        final RestModel annotation = cls.getAnnotation(RestModel.class);

        if (annotation == null) {
            throw new ElepyConfigException("Resources must have the @RestModel Annotation");
        }

        return (Crud<T>) getSingleton(Crud.class, annotation.slug());
    }

    @Override
    public ObjectMapper getObjectMapper() {
        return getSingleton(ObjectMapper.class);
    }

}