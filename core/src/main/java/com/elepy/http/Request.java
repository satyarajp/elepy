package com.elepy.http;

import com.elepy.auth.*;
import com.elepy.dao.Filter;
import com.elepy.dao.*;
import com.elepy.di.ElepyContext;
import com.elepy.exceptions.ElepyException;
import com.elepy.models.Schema;
import com.elepy.uploads.FileUpload;
import com.elepy.utils.ReflectionUtils;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static com.elepy.dao.Filters.*;
import static com.elepy.dao.Queries.create;

public interface Request {

    ObjectMapper DEFAULT_MAPPER = new ObjectMapper();

    String params(String param);

    String method();

    String scheme();

    String host();

    int port();

    String url();

    String ip();

    String body();

    byte[] bodyAsBytes();

    String queryParams(String queryParam);

    String queryParamOrDefault(String queryParam, String defaultValue);

    String headers(String header);

    <T> T attribute(String attribute);

    Map<String, String> cookies();

    String cookie(String name);

    String uri();

    Session session();

    Set<String> queryParams();

    Set<String> headers();

    String queryString();

    Map<String, String> params();

    String[] queryParamValues(String key);

    List<FileUpload> uploadedFiles(String key);

    default FileUpload uploadedFile(String key) {

        final List<FileUpload> fileUploads = uploadedFiles(key);

        return fileUploads.isEmpty() ? null : fileUploads.get(0);
    }

    default String token() {
        final var authorization = headers("Authorization");

        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.split(" ")[1];
        }
        return cookie("ELEPY_TOKEN");
    }

    void attribute(String attribute, Object value);

    Set<String> attributes();

    /**
     * @return The ID of the model a.k.a request.params("id")
     */
    default Serializable recordId() {

        final String id = queryParams("id");

        if (id != null) {
            return ReflectionUtils.toObjectIdFromString(attribute("modelClass"), id);
        }

        final String ids = queryParams("ids");

        if (ids != null) {
            final String[] split = ids.split(",");

            return ReflectionUtils.toObjectIdFromString(attribute("modelClass"), split[0]);
        }

        return recordId(attribute("modelClass"));
    }


    default <T> T inputAs(Class<T> t) {
        try {

            if (String.class.equals(t)) {
                final JsonNode jsonNode = DEFAULT_MAPPER.readTree(body());

                if (jsonNode.isTextual()) {
                    return (T) jsonNode.asText();
                } else if (jsonNode.fields().hasNext()) {
                    return (T) jsonNode.fields().next().getValue().asText();
                } else {
                    throw new ElepyException("Can't extract string from " + body(), 500);
                }
            }
            return DEFAULT_MAPPER.readValue(body(), t);
        } catch (JsonParseException e) {
            throw new ElepyException("Invalid JSON: " + e.getMessage(), 400, e);
        } catch (JsonProcessingException e) {
            throw new ElepyException("Error processing JSON: " + e.getMessage(), 500, e);
        }
    }

    default String inputAsString() {
        return inputAs(String.class);
    }

    default Set<Serializable> recordIds() {
        final String ids = queryParams("ids");

        if (ids != null) {
            final String[] split = ids.split(",");
            return Arrays.stream(split).map(s -> ReflectionUtils.toObjectIdFromString(attribute("modelClass"), s)).collect(Collectors.toSet());
        }
        return new HashSet<>(Collections.singletonList(recordId()));
    }

    default UserAuthenticationExtension userAuthenticationCenter() {
        return attribute("authCenter");
    }

    default ElepyContext elepy() {
        return attribute("elepyContext");
    }

    default void validate(Object o) {
        final var violations = elepy().validator().validate(o);
        if (!violations.isEmpty()) {
            var message = violations.stream()
                    .map(cv -> cv == null ? "null" : cv.getPropertyPath().toString().replaceAll("\\.", " -> ") + ": " + cv.getMessage())
                    .collect(Collectors.joining(",\n"));

            throw new ElepyException(message);
        }
    }

    default UserAuthenticationExtension authService() {
        final var elepy = elepy();
        if (elepy == null) {
            return null;
        }
        return elepy.getDependency(UserAuthenticationExtension.class);
    }

    default Optional<User> loggedInUser() {
        final User userFromAttributes = attribute("user");
        if (userFromAttributes != null) {
            return Optional.of(userFromAttributes);
        }

        final var userCenter = elepy().getDependency(UserCenter.class);
        final var user = grant().flatMap(userCenter::getUserFromGrant);
        user.ifPresent(u -> attribute("user", user));

        return user;
    }

    default Optional<Grant> grant() {
        if (authService() == null) {
            return Optional.empty();
        }
        return authService().getGrant(this);
    }

    default User loggedInUserOrThrow() {
        return loggedInUser().orElseThrow(() -> new ElepyException("Must be logged in.", 401));
    }

    default Permissions permissions() {
        Permissions permissions = Optional.ofNullable((Permissions) attribute("permissions")).orElse(new Permissions());

        grant().ifPresent(user -> {
            permissions.grantPermission(Permissions.AUTHENTICATED);
            permissions.grantPermission(user.getPermissions());
        });

        attribute("permissions", permissions);

        return permissions;
    }

    default void addPermissions(String... permissions) {
        permissions().grantPermission(permissions);
    }


    default boolean hasPermissions(Collection<String> requiredPermissions) {
        return permissions().hasPermissions(requiredPermissions);
    }

    default void requirePermissions(String... requiredPermissions) {
        requirePermissions(Arrays.asList(requiredPermissions));
    }

    default void requirePermissions(Collection<String> requiredPermissions) {
        if (!hasPermissions(requiredPermissions)) {
            throw new ElepyException("User is not authorized.", 401);
        }
    }


    /**
     * @return The ID of the model a.k.a request.params("id)
     */
    default Serializable recordId(Class cls) {

        String id = params("id");
        if (cls == null) {
            try {
                return Integer.parseInt(id);
            } catch (Exception e) {
                try {
                    return Long.parseLong(id);
                } catch (Exception e1) {
                    return id;
                }
            }
        } else {
            return ReflectionUtils.toObjectIdFromString(cls, id);
        }
    }


    default SortingSpecification sortingForModel(Schema<?> schema) {


        String[] sorts = queryParamValues("sort");
        SortingSpecification sortingSpecification = new SortingSpecification();


        if (sorts == null || sorts.length == 0) {
            sortingSpecification.add(schema.getDefaultSortField(), schema.getDefaultSortDirection());
        } else {
            for (String sort : sorts) {
                String[] split = sort.split(",");

                if (split.length == 1) {
                    sortingSpecification.add(split[0], SortOption.ASCENDING);
                } else {
                    sortingSpecification.add(split[0], SortOption.get(split[1]));
                }
            }
        }


        return sortingSpecification;
    }

    default Query parseQuery() {

        String q = Optional.ofNullable(queryParams("q")).orElse("");

        final var sortingSpec = sortingForModel(schema(null).orElseThrow());
        String ps = queryParams("pageSize");
        String pn = queryParams("pageNumber");
        int pageSize = ps == null ? Integer.MAX_VALUE : Integer.parseInt(ps);
        int pageNumber = pn == null ? 1 : Integer.parseInt(pn);

        final var or = or(filtersForModel(null));
        return create(and(Queries.parse(q).getExpression(), or.getExpressions().isEmpty() ? search("") : or))
                .purge().sort(sortingSpec).page(pageNumber, pageSize);
    }

    default <T> Optional<Schema> schema(Class<T> t) {
        final var restModelType = Optional.ofNullable(t).orElse(attribute("modelClass"));

        if (restModelType == null) {
            return Optional.empty();
        }
        List<Schema> schemas = Optional.ofNullable((List<Schema>) attribute("schemas")).orElse(List.of());

        return schemas.stream().filter(s -> s.getJavaClass().equals(restModelType)).findFirst();

    }

    @SuppressWarnings("unchecked")
    default List<Filter> filtersForModel(Class modelClass) {

        final Optional<Schema> schema = schema(modelClass);
        final List<Filter> filterQueries = new ArrayList<>();
        for (String queryParam : queryParams()) {
            if (queryParam.contains("_")) {
                String[] propertyNameFilter = queryParam.split("_");

                //Get the property name like this, incase you have a property called 'customer_type'
                List<String> propertyNameList = new ArrayList<>(Arrays.asList(propertyNameFilter));
                propertyNameList.remove(propertyNameFilter.length - 1);
                String propertyName = String.join("_", propertyNameList);

                FilterType.getByQueryString(propertyNameFilter[propertyNameFilter.length - 1]).ifPresent(filterType1 -> {

                    schema.ifPresent(schema1 -> {
                        final var property = schema1.getProperty(propertyName);
                        if (!filterType1.canBeUsedBy(property)) {
                            throw new ElepyException(String.format("'%s' can't be applied to the field '%s'", filterType1.getPrettyName(), property.getPrettyName()), 400);
                        }
                    });

                    Filter filter = new Filter(propertyName, filterType1, queryParams(queryParam));
                    filterQueries.add(filter);
                });
            }
        }
        return filterQueries;
    }

}
