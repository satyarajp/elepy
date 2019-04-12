package com.elepy.mongo;

import com.elepy.Elepy;
import com.elepy.dao.Crud;
import com.elepy.dao.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.fakemongo.Fongo;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mongodb.DB;
import com.mongodb.FongoDB;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class ElepyEndToEndTest extends Base {

    private static Elepy elepy;
    private static Crud<Resource> defaultMongoDao;

    @BeforeAll
    public static void beforeAll() throws Exception {


        elepy = new Elepy();
        Fongo fongo = new Fongo("test");


        final FongoDB db = fongo.getDB("test");


        elepy.registerDependency(DB.class, db);


        elepy.addModel(Resource.class);

        elepy.onPort(7357);
        elepy.withDefaultCrudProvider(MongoProvider.class);

        elepy.start();

        defaultMongoDao = elepy.getCrudFor(Resource.class);
    }


    @Test
    public void testFind() throws IOException, UnirestException {


        final long count = defaultMongoDao.count();
        defaultMongoDao.create(validObject());
        final HttpResponse<String> getRequest = Unirest.get("http://localhost:7357/resources").asString();


        Page resourcePage = elepy.getObjectMapper().readValue(getRequest.getBody(), Page.class);

        assertEquals(count + 1, resourcePage.getValues().size());

        Assertions.assertEquals(200, getRequest.getStatus());
    }

    @Test
    public void testFilterAndSearch() throws IOException, UnirestException {
        Resource resource = validObject();
        resource.setUnique("filterUnique");
        resource.setNumberMax40(BigDecimal.valueOf(25));
        resource.setId(4);
        defaultMongoDao.create(resource);
        defaultMongoDao.create(validObject());
        final HttpResponse<String> getRequest = Unirest.get("http://localhost:7357/resources?id_equals=4&unique_contains=filter&numberMax40_equals=25&q=ilterUni").asString();


        Page<Resource> resourcePage = elepy.getObjectMapper().readValue(getRequest.getBody(), new TypeReference<Page<Resource>>() {
        });

        assertEquals(1, resourcePage.getValues().size());

        Assertions.assertEquals(200, getRequest.getStatus());
        assertEquals("filterUnique", resourcePage.getValues().get(0).getUnique());
    }

    @Test
    public void testSearchNotFindingAnything() throws IOException, UnirestException {
        Resource resource = validObject();
        resource.setUnique("testSearchNotFindingAnything");
        resource.setNumberMax40(BigDecimal.valueOf(25));
        defaultMongoDao.create(resource);
        defaultMongoDao.create(validObject());
        final HttpResponse<String> getRequest = Unirest.get("http://localhost:7357/resources?q=ilterUni").asString();


        Page<Resource> resourcePage = elepy.getObjectMapper().readValue(getRequest.getBody(), new TypeReference<Page<Resource>>() {
        });

        assertEquals(0, resourcePage.getValues().size());

        Assertions.assertEquals(200, getRequest.getStatus());
    }

    @Test
    public void testSearch() throws IOException, UnirestException {
        Resource resource = validObject();
        resource.setUnique("testSearchTo2");
        resource.setNumberMax40(BigDecimal.valueOf(25));
        defaultMongoDao.create(resource);
        defaultMongoDao.create(validObject());
        final HttpResponse<String> getRequest = Unirest.get("http://localhost:7357/resources?q=testsearchto").asString();


        Page<Resource> resourcePage = elepy.getObjectMapper().readValue(getRequest.getBody(), new TypeReference<Page<Resource>>() {
        });

        assertEquals(1, resourcePage.getValues().size());

        Assertions.assertEquals(200, getRequest.getStatus());
    }

    @Test
    public void testFindOne() throws IOException, UnirestException {


        defaultMongoDao.create(validObject());

        final Resource resource = defaultMongoDao.getAll().get(0);
        final HttpResponse<String> getRequest = Unirest.get("http://localhost:7357/resources/" + resource.getId()).asString();

        Resource foundResource = elepy.getObjectMapper().readValue(getRequest.getBody(), Resource.class);

        assertEquals(foundResource.getId(), resource.getId());

        Assertions.assertEquals(200, getRequest.getStatus());
    }

    @Test
    public void testCreate() throws UnirestException, JsonProcessingException {

        final long count = defaultMongoDao.count();
        final Resource resource = validObject();
        resource.setUnique("uniqueCreate");
        final String s = elepy.getObjectMapper().writeValueAsString(resource);

        final HttpResponse<String> postRequest = Unirest.post("http://localhost:7357/resources").body(s).asString();

        assertEquals(count + 1, defaultMongoDao.count());
        Assertions.assertEquals(201, postRequest.getStatus());
    }


    @Test
    public void testMultiCreate_atomicCreateInsertsNone_OnIntegrityFailure() throws UnirestException, JsonProcessingException {

        final long count = defaultMongoDao.count();

        final Resource resource = validObject();

        resource.setUnique("uniqueMultiCreate");

        final Resource resource1 = validObject();
        resource1.setUnique("uniqueMultiCreate");


        final String s = elepy.getObjectMapper().writeValueAsString(new Resource[]{resource, resource1});

        final HttpResponse<String> postRequest = Unirest.post("http://localhost:7357/resources").body(s).asString();

        assertEquals(count, defaultMongoDao.count());
    }

    @Test
    public void testMultiCreate() throws UnirestException, JsonProcessingException {

        final long count = defaultMongoDao.count();
        final Resource resource = validObject();

        resource.setUnique("uniqueMultiCreate");

        final Resource resource1 = validObject();
        resource1.setUnique("uniqueMultiCreate1");

        final String s = elepy.getObjectMapper().writeValueAsString(new Resource[]{resource, resource1});

        final HttpResponse<String> postRequest = Unirest.post("http://localhost:7357/resources").body(s).asString();

        assertEquals(count + 2, defaultMongoDao.count());
        Assertions.assertEquals(201, postRequest.getStatus());
    }

    @Test
    void testDelete() throws UnirestException {

        final long beginningCount = defaultMongoDao.count();
        final Resource resource = validObject();

        resource.setId(55);

        defaultMongoDao.create(resource);

        assertEquals(beginningCount + 1, defaultMongoDao.count());
        final HttpResponse<String> delete = Unirest.delete("http://localhost:7357/resources/55").asString();

        assertEquals(beginningCount, defaultMongoDao.count());
        Assertions.assertEquals(200, delete.getStatus());

    }

    @Test
    void testUpdatePartial() throws UnirestException {
        final long beginningCount = defaultMongoDao.count();
        final Resource resource = validObject();

        resource.setId(66);
        resource.setMARKDOWN("ryan");


        defaultMongoDao.create(resource);

        assertEquals(beginningCount + 1, defaultMongoDao.count());
        final HttpResponse<String> patch = Unirest.patch("http://localhost:7357/resources/66").body("{\"id\":" + resource.getId() + ",\"unique\": \"uniqueUpdate\"}").asString();

        assertEquals(beginningCount + 1, defaultMongoDao.count());


        Optional<Resource> updatePartialId = defaultMongoDao.getById(66);

        assertTrue(updatePartialId.isPresent());
        assertEquals("uniqueUpdate", updatePartialId.get().getUnique());
        assertEquals("ryan", updatePartialId.get().getMARKDOWN());
        Assertions.assertEquals(200, patch.getStatus());
    }

    @Test
    void testExtraRouteInService() throws UnirestException {

        final String shouldReturn = "I am here";
        Resource resource1 = validObject();
        resource1.setId(77);
        resource1.setTextField(shouldReturn);
        defaultMongoDao.create(resource1);
        final HttpResponse<String> getRequest = Unirest.get("http://localhost:7357/resources/" + resource1.getId() + "/extra").asString();

        Assertions.assertEquals(200, getRequest.getStatus());
        Assertions.assertEquals(shouldReturn, getRequest.getBody());
    }

    @Test
    void testExtraRoute() throws UnirestException {
        final HttpResponse<String> getRequest = Unirest.get("http://localhost:7357/resources-extra").asString();

        Assertions.assertEquals(201, getRequest.getStatus());
        Assertions.assertEquals("generated", getRequest.getBody());
    }

    @Test
    void testAction() throws UnirestException {
        final HttpResponse<String> getRequest = Unirest.get("http://localhost:7357/resources/actions/extra-action?ids=999,777").asString();

        Assertions.assertEquals(200, getRequest.getStatus());
        Assertions.assertEquals("[999,777]", getRequest.getBody());
    }
}