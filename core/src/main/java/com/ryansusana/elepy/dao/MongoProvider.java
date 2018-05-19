package com.ryansusana.elepy.dao;

import com.ryansusana.elepy.annotations.RestModel;

public class MongoProvider extends CrudProvider {

    public MongoProvider() {
    }

    @Override
    public <T> Crud<T> crudFor(Class<T> type) {
        final RestModel model = type.getAnnotation(RestModel.class);

        return new MongoDao<T>(elepy().getDb(), model.slug().replaceAll("/", ""), type);
    }
}
