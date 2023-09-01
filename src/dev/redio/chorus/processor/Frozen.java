package dev.redio.chorus.processor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import dev.redio.chorus.Freezable;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.SOURCE)
public @interface Frozen {
    @SuppressWarnings("rawtypes")
    Class<? extends Freezable> mutableType() default Freezable.class; 
}
