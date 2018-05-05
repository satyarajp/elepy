package com.ryansusana.elepy.admin;

import com.ryansusana.elepy.Elepy;
import com.ryansusana.elepy.ElepyModule;
import com.ryansusana.elepy.admin.annotations.View;
import com.ryansusana.elepy.admin.concepts.ResourceView;
import com.ryansusana.elepy.admin.dao.UserDao;
import com.ryansusana.elepy.admin.models.User;
import com.ryansusana.elepy.admin.models.UserType;
import com.ryansusana.elepy.admin.services.BCrypt;
import com.ryansusana.elepy.admin.services.UserService;
import com.ryansusana.elepy.models.RestErrorMessage;
import com.ryansusana.elepy.utils.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.ModelAndView;
import spark.Service;
import spark.template.pebble.PebbleTemplateEngine;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;


public class ElepyAdminPanel extends ElepyModule {
    public static final String ADMIN_USER = "adminUser";
    private static final Logger LOGGER = LoggerFactory.getLogger(ElepyAdminPanel.class);
    private final UserDao userDao;
    private final UserService userService;
    private boolean initiated = false;

    private final Set<ElepyAdminPanelPlugin> plugins;


    public ElepyAdminPanel(Elepy elepy, Service service) {
        super(elepy, service);
        this.userDao = new UserDao(elepy.getDb(), elepy.getMapper());
        this.userService = new UserService(userDao);
        this.plugins = new TreeSet<>();

    }

    public ElepyAdminPanel(Elepy elepy) {
        super(elepy);
        this.userDao = new UserDao(elepy.getDb(), elepy.getMapper());
        this.userService = new UserService(userDao);
        this.plugins = new TreeSet<>();

    }


    @Override
    public void routes() {

        try {
            setupLogin();
            setupAdmin();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
    }


    @Override
    public void setup() {
        initiated = true;
        http().staticFileLocation("/admin-panel-public");
        elepy().addPackage(User.class.getPackage().getName());

    }

    private void defaultDecriptorPanel(Map<String, Object> descriptor, List<Map<String, Object>> descriptors) {
        http().get("/admin" + descriptor.get("slug"), (request, response) -> {

            Map<String, Object> model = new HashMap<>();

            model.put("descriptors", descriptors);
            model.put("plugins", plugins);

            model.put("currentDescriptor", descriptor);
            return render(model, "templates/model.peb");
        });
    }

    private void setupAdmin() throws ClassNotFoundException, IllegalAccessException, InvocationTargetException, InstantiationException {
        List<Map<String, Object>> descriptors = new ArrayList<>();
        for (Object o : elepy().getDescriptors()) {
            if (o instanceof Map) {
                descriptors.add((Map<String, Object>) o);
            }
        }
        for (Map<String, Object> descriptor : descriptors) {
            http().get("/admin/config" + descriptor.get("slug"), (request, response) -> {
                response.type("application/json");
                return elepy().getObjectMapper().writeValueAsString(
                        descriptor
                );
            });

            if (descriptor.containsKey("javaClass")) {
                final Class<?> javaClass = Class.forName((String) descriptor.get("javaClass"));

                if (javaClass.isAnnotationPresent(View.class)) {

                    final View annotation = javaClass.getAnnotation(View.class);

                    final Optional<Constructor<?>> emptyConstructor = ClassUtils.getEmptyConstructor(annotation.value());

                    if (!emptyConstructor.isPresent()) {
                        throw new IllegalAccessException("Resource View Class must contain an empty constructor");
                    }
                    final ResourceView resourceView = (ResourceView) emptyConstructor.get().newInstance();
                    http().get("/admin" + descriptor.get("slug"), (request, response) -> {

                        Map<String, Object> model = new HashMap<>();

                        model.put("customView", resourceView.renderView(descriptor));
                        model.put("descriptors", descriptors);
                        model.put("plugins", plugins);

                        model.put("currentDescriptor", descriptor);
                        return render(model, "templates/model.peb");
                    });
                } else {
                    defaultDecriptorPanel(descriptor, descriptors);
                }
            } else {
                defaultDecriptorPanel(descriptor, descriptors);
            }


        }


        http().before("/admin/*/*", (request, response) -> elepy().allAdminFilters().handle(request, response));
        http().before("/plugins/*", (request, response) -> elepy().allAdminFilters().handle(request, response));
        http().before("/plugins/*/*", (request, response) -> elepy().allAdminFilters().handle(request, response));
        http().before("/admin/*", (request, response) -> {
            elepy().allAdminFilters().handle(request, response);
        });
        http().before("/admin", (request, response) -> {
            elepy().allAdminFilters().handle(request, response);
        });
        http().get("/admin", (request, response) -> {

            Map<String, Object> model = new HashMap<>();
            model.put("descriptors", descriptors);
            model.put("plugins", plugins);
            return render(model, "templates/base.peb");
        });
        http().get("/admin-logout", (request, response) -> {

            request.session().invalidate();
            response.redirect("/login");

            return "";
        });
        for (ElepyAdminPanelPlugin plugin : this.plugins) {
            plugin.setup(http(), elepy().getDb(), elepy().getObjectMapper());
            http().get("/plugins/" + plugin.getSlug(), (request, response) -> {
                Map<String, Object> model = new HashMap<>();
                String content = plugin.renderContent(null);
                model.put("descriptors", descriptors);
                model.put("content", content);
                model.put("plugin", plugin);
                model.put("plugins", plugins);
                return render(model, "templates/plugin.peb");
            });
        }
    }

    private Map<String, Object> getDescriptor(String slug, List<Map<String, Object>> descriptors) {
        for (Map<String, Object> descriptor : descriptors) {
            if (descriptor.get("slug").equals(slug)) {
                return descriptor;
            }
        }
        return null;
    }

    private void setupLogin() {


        elepy().addAdminFilter((request, response) -> {
            final User adminUser = request.session().attribute(ADMIN_USER);
            if (adminUser == null) {
                request.session().attribute("redirectUrl", request.uri());
                response.redirect("/login");
            }
        });
        http().get("/login", (request, response) -> {


            return render(new HashMap<>(), "templates/login.peb");
        });
        http().post("/login", (request, response) -> {

            final Optional<User> user = userService.login(request.queryParamOrDefault("username", "invalid"), request.queryParamOrDefault("password", "invalid"));

            final String redirectUrl = request.session().attribute("redirectUrl") == null ? "/admin" : request.session().attribute("redirectUrl");


            if (user.isPresent()) {
                if (user.get().getUserType().getLevel() < 0) {
                    throw new RestErrorMessage("Your account has been suspended!");
                }
                request.session().attribute(ADMIN_USER, user.get());
                response.status(200);
                request.session().removeAttribute("redirectUrl");
                return redirectUrl;
            }

            response.status(401);
            return "Invalid login credentials";
        });
        http().get("/setup", (request, response) -> {

            if (userDao.count() == 0) {
                User user = new User(null, "admin", BCrypt.hashpw("admin", BCrypt.gensalt()), "", UserType.SUPER_ADMIN);
                userDao.create(user);
                return "Generated first admin account";
            }
            response.redirect("/admin");
            return "templates/base.peb";
        });


    }

    private String render(Map<String, Object> model, String templatePath) {
        return new PebbleTemplateEngine().render(new ModelAndView(model, templatePath));
    }

    public ElepyAdminPanel addPlugin(ElepyAdminPanelPlugin plugin) {
        if (initiated) {
            throw new IllegalStateException("Can't add plugins after setup() has been called!");
        }
        plugin.setAdminPanel(this);
        this.plugins.add(plugin);
        return this;
    }

}
