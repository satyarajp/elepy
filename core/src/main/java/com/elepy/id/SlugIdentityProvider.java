package com.elepy.id;

import com.elepy.dao.Crud;
import com.elepy.exceptions.ElepyException;
import com.elepy.utils.ReflectionUtils;
import com.github.slugify.Slugify;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * This {@link IdentityProvider} creates an SEO-friendly link Path for an ID.
 *
 * @param <T> The model type
 */
public class SlugIdentityProvider<T> implements IdentityProvider<T> {
    private static final Logger logger = LoggerFactory.getLogger(SlugIdentityProvider.class);
    private final String[] slugFieldNames;
    private final Slugify slugify;
    private final int maxLength;


    public SlugIdentityProvider() {
        this(70, "name", "title", "slug");
    }

    public SlugIdentityProvider(int maxLength, String... slugFieldNames) {
        this.maxLength = maxLength;
        this.slugify = new Slugify();
        this.slugFieldNames = slugFieldNames;
    }


    @Override
    public void provideId(T item, Crud<T> dao) {
        final String path = getPath(item, Arrays.asList(slugFieldNames)).orElseThrow(() -> new ElepyException("There is no available path property. This must be a String."));
        String generatedPath = generatePath(path, 0, dao);

        Field field = ReflectionUtils.getIdField(dao.getType()).orElseThrow(() -> new ElepyException("No ID field", 500));

        field.setAccessible(true);

        try {
            field.set(item, generatedPath);
        } catch (IllegalAccessException e) {
            throw new ElepyException("Failed to reflectively access: " + field.getName(), 500);
        }

    }

    private Optional<String> getPath(T obj, List<String> slugFieldNames) {
        for (Field field : obj.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            final Object value;
            try {
                value = field.get(obj);
            } catch (IllegalAccessException e) {
                logger.error(e.getMessage(), e);
                continue;
            }
            if (slugFieldNames.contains(field.getName()) && (value instanceof String)) {
                return Optional.of(slugify.slugify(getSubStrVersion((String) value, maxLength)));
            }
        }
        return Optional.empty();
    }

    private String getSubStrVersion(String s, int maxLen) {
        if (s.length() < maxLen) {
            return s;
        }

        return s.substring(0, maxLen);
    }

    private String generatePath(String slug, int iteration, Crud crud) {
        String generatedId = slug;

        if (iteration > 0) {
            generatedId += "-" + (iteration + 1);
        }

        if (crud.getById(generatedId).isPresent()) {
            return generatePath(slug, iteration + 1, crud);
        }

        return generatedId;
    }
}
