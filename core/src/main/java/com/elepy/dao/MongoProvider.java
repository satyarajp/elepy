package com.elepy.dao;

import com.elepy.annotations.RestModel;
import com.mongodb.DB;

public class MongoProvider<T> extends CrudProvider<T> {

    public MongoProvider() {
    }

    @Override
    public Crud<T> crudFor(Class<T> type) {
        final RestModel model = type.getAnnotation(RestModel.class);

        return new MongoDao<T>(elepy().getSingleton(DB.class), model.slug().replaceAll("/", ""), type);
    }
}