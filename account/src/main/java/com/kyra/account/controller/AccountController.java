package com.kyra.account.controller;

import com.kyra.account.bean.Salt;
import com.kyra.account.handler.AuthFormLoginHandler;
import com.kyra.account.provider.PgAuthProvider;
import com.kyra.account.router.Route;
import com.kyra.account.service.AccountService;
import com.kyra.account.service.impl.AccountServiceImpl;
import com.kyra.common.bean.Field;
import com.kyra.common.bean.UserImpl;
import com.kyra.common.controller.CommonController;
import com.kyra.common.session.AuthCookie;
import com.kyra.common.session.RedisSession;
import com.kyra.common.utils.Render;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.common.template.TemplateEngine;
import io.vertx.ext.web.handler.FormLoginHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.TemplateHandler;
import io.vertx.ext.web.sstore.SessionStore;
import io.vertx.ext.web.templ.handlebars.HandlebarsTemplateEngine;

import java.util.UUID;

public class AccountController extends CommonController {
  private final AccountService accountService = new AccountServiceImpl();
  private final FormLoginHandler formLoginHandler;
  private final Router router;
  private final TemplateEngine templateEngine;

  public AccountController(Vertx vertx, Router router, SessionStore sessionStore) {
    super(sessionStore);
    AuthProvider authProvider = new PgAuthProvider();
    formLoginHandler = new AuthFormLoginHandler()
      .setSessionStore(sessionStore)
      .setAuthProvider(authProvider)
      .setUsernameParam("email")
      .setPasswordParam("password")
      .setDirectLoggedInOKURL(Route.INDEX.path());
    this.router = router;
    templateEngine = HandlebarsTemplateEngine.create(vertx);

    initRouter();
  }

  private void initRouter() {
    router.get(Route.STATIC.path()).handler(StaticHandler.create("static"));
    router.getWithRegex(Route.HANDLEBARS_TEMPLATES.path()).handler(TemplateHandler.create(templateEngine));
    router.get(Route.SIGN_UP.path()).handler(this::signUp);
    router.post(Route.SIGN_UP.path()).handler(this::registerRequestValidation).handler(this::userUniquenessCheck).handler(this::register);
    router.get(Route.SIGN_IN.path()).handler(this::sessionCheck).handler(this::signIn);
    router.post(Route.SIGN_IN.path()).handler(formLoginHandler);
    router.get(Route.SIGN_OUT.path()).handler(this::signOut);
    router.get(Route.USER.path()).handler(sessionHandler).handler(this::userInfo);
    router.get(Route.SEARCH.path()).handler(sessionHandler).handler(this::checkSearch).handler(this::search);
  }

  private void checkSearch(RoutingContext rc) {
    if (!rc.request().params().contains("q")) {
      Render.badRequest(rc);
    } else rc.next();
  }

  private void search(RoutingContext rc) {
    String q = rc.request().getParam("q");
    accountService.search(q, rc.user(), ar -> {
      if (ar.failed()) {
        log.error(String.format("[%s] Failed to search for user: %s", AccountController.class, q), ar.cause());
        Render.internalServerError(rc);
      } else Render.ok(rc, new JsonArray(ar.result()));
    });
  }

  private void userInfo(RoutingContext rc) {
    Render.ok(rc, rc.user().principal());
  }

  private void userUniquenessCheck(RoutingContext rc) {
    String email = rc.request().formAttributes().get(Field.email.name());
    accountService.findUser(email, ar -> {
      if (ar.failed()) {
        rerouteToSignUp(rc, "An error occurred while registering your account");
      } else {
        if (ar.result() == null) rc.next();
        else {
          rc.put(Field.email.name(), email);
          rc.put(Field.first_name.name(), rc.request().formAttributes().get(Field.first_name.name()));
          rc.put(Field.last_name.name(), rc.request().formAttributes().get(Field.last_name.name()));
          rerouteToSignUp(rc, "This email address is already registered");
        }
      }
    });
  }

  /**
   * Check if session exists.
   * If session exists redirect to index page
   * else call next middleware
   *
   * @param rc Current routing context
   */
  private void sessionCheck(RoutingContext rc) {
    Cookie cookie = rc.getCookie(AuthCookie.NAME);
    if (cookie == null) {
      rc.next();
      return;
    }

    sessionStore.get(cookie.getValue(), ar -> {
      if (ar.failed() || ar.result() == null) rc.next();
      else Render.redirect(rc, Route.INDEX.path());
    });
  }

  /**
   * Check register request validity. Check if first name, last name, email and password are not null.
   * In case of one of those parameter is null or empty string, it returns a bad request error code.
   * Otherwise, it calls the next handler to process register request.
   *
   * @param rc Current routing context
   */
  private void registerRequestValidation(RoutingContext rc) {
    try {
      MultiMap body = rc.request().formAttributes();
      String firstName = body.get(Field.first_name.name());
      String lastName = body.get(Field.last_name.name());
      String email = body.get(Field.email.name());
      String password = body.get(Field.password.name());

      if ("".equals(firstName.trim()) || "".equals(lastName.trim()) || "".equals(email.trim()) || "".equals(password.trim())) {
        Render.badRequest(rc);
        return;
      }

      rc.next();
    } catch (NullPointerException e) {
      Render.badRequest(rc);
    }
  }

  private void register(RoutingContext rc) {
    MultiMap body = rc.request().formAttributes();
    String firstName = body.get(Field.first_name.name());
    String lastName = body.get(Field.last_name.name());
    String email = body.get(Field.email.name());
    String password = body.get(Field.password.name());
    accountService.register(email, firstName, lastName, new Salt(password).SHA1(), ar -> {
      if (ar.failed()) Render.internalServerError(rc);
      else {
        User user = new UserImpl(ar.result());
        String sessionId = UUID.randomUUID().toString();
        Session session = new RedisSession(sessionId, user.principal().getMap());
        sessionStore.put(session, sh -> {
          if (sh.failed()) Render.internalServerError(rc);
          else {
            rc.setUser(user);
            rc.addCookie(Cookie.cookie(AuthCookie.NAME, sessionId).setPath(Route.INDEX.path()));
            Render.redirect(rc, Route.INDEX.path());
          }
        });
      }
    });
  }

  private void signIn(RoutingContext rc) {
    rerouteToSignIn(rc, null);
  }

  public static void rerouteToSignIn(RoutingContext rc, String errorMessage) {
    reroute(rc, HttpMethod.GET, "/sign_in.hbs", "Sign in", errorMessage);
  }


  private void signUp(RoutingContext rc) {
    rerouteToSignUp(rc, null);
  }

  private void rerouteToSignUp(RoutingContext rc, String errorMessage) {
    reroute(rc, HttpMethod.GET, "/sign_up.hbs", "Sign up", errorMessage);
  }

  private static void reroute(RoutingContext rc, HttpMethod method, String path, String title, String errorMessage) {
    if (errorMessage != null && !"".equals(errorMessage.trim())) {
      rc.put("error_message", errorMessage);
    }

    rc.put("title", title);
    rc.reroute(method, path);
  }

  /**
   * Sign out current user. Delete session in store and remove current cookie.
   *
   * @param rc Current routing Context
   */
  private void signOut(RoutingContext rc) {
    Cookie cookie = rc.getCookie(AuthCookie.NAME);
    if (cookie == null) {
      Render.redirect(rc, Route.SIGN_IN.path());
      return;
    }

    sessionStore.delete(cookie.getValue(), ar -> {
      if (ar.failed()) {
        Render.internalServerError(rc);
      } else {
        rc.removeCookie(AuthCookie.NAME, true);
        Render.redirect(rc, Route.SIGN_IN.path());
      }
    });
  }
}
