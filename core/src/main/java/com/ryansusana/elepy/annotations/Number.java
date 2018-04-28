package com.ryansusana.elepy.annotations;

import com.ryansusana.elepy.models.NumberType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface Number {
    NumberType value() default NumberType.DECIMAL;

    float minimum() default Integer.MIN_VALUE;

    float maximum() default Integer.MAX_VALUE;
}
