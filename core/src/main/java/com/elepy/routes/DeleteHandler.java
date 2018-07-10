package com.elepy.routes;

import com.elepy.dao.Crud;
import com.fasterxml.jackson.databind.ObjectMapper;
import spark.Request;
import spark.Response;


public interface DeleteHandler<T> {
    boolean delete(Request request, Response response, Crud<T> dao, ObjectMapper objectMapper);
}
