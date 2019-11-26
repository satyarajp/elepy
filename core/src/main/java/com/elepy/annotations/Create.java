package com.elepy.annotations;


import com.elepy.auth.Permissions;
import com.elepy.handlers.CreateHandler;
import com.elepy.handlers.DefaultCreate;

import java.lang.annotation.*;

/**
 * Annotation used to change the way Elepy handles creates.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Inherited
public @interface Create {

    /**
     * The class that handles the functionality of creates on this Resource.
     *
     * @return the route createHandler
     * @see DefaultCreate
     * @see com.elepy.handlers.CreateHandler
     */
    Class<? extends CreateHandler> handler() default DefaultCreate.class;

    /**
     * A list of required permissions to execute this A
     */
    String[] requiredPermissions() default Permissions.AUTHENTICATED;
}