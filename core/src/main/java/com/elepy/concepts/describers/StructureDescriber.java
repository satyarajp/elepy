package com.elepy.concepts.describers;

import com.elepy.annotations.Generated;
import com.elepy.annotations.Hidden;
import com.elepy.annotations.PrettyName;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StructureDescriber {

    private final Class cls;

    private final List<Map<String, Object>> structure;

    public StructureDescriber(Class cls) {
        this.cls = cls;
        this.structure = describe();
    }

    private List<Map<String, Object>> describe() {
        List<Map<String, Object>> fields = new ArrayList<>();

        fields.addAll(describeFields());
        fields.addAll(describeMethods());

        fields.sort((a, b) -> {
            final Integer importanceA = (Integer) a.getOrDefault("importance", 0);
            final Integer importanceB = (Integer) b.getOrDefault("importance", 0);


            return importanceB.compareTo(importanceA);
        });
        return fields;
    }

    private List<Map<String, Object>> describeFields() {
        List<Map<String, Object>> fields = new ArrayList<>();
        for (Field field : cls.getDeclaredFields()) {
            if (!field.isAnnotationPresent(Hidden.class))
                fields.add(new FieldDescriber(field).getFieldMap());
        }

        return fields;

    }

    private List<Map<String, Object>> describeMethods() {
        List<Map<String, Object>> methods = new ArrayList<>();

        for (Method method : cls.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Generated.class)) {
                methods.add(new MethodDescriber(method).getFieldMap());
            }
        }

        return methods;
    }

    public List<Map<String, Object>> getStructure() {
        return structure;
    }

    static List<Map<String, Object>> getEnumMap(Class<?> enumClass) {

        List<Map<String, Object>> toReturn = new ArrayList<>();
        for (Object enumConstant : enumClass.getEnumConstants()) {
            Map<String, Object> toAdd = new HashMap<>();

            toAdd.put("enumValue", enumConstant);

            Field declaredField = null;
            try {
                declaredField = enumClass.getDeclaredField(((Enum) enumConstant).name());
            } catch (NoSuchFieldException ignored) {
                //this exception will never be thrown
            }

            PrettyName annotation = declaredField.getAnnotation(PrettyName.class);
            if (annotation != null) {

                toAdd.put("enumName", annotation.value());
            } else {
                toAdd.put("enumName", ((Enum) enumConstant).name());
            }
            toReturn.add(toAdd);

        }
        return toReturn;
    }
}
