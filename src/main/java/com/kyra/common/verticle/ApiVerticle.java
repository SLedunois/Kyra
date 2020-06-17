package com.kyra.common.verticle;

import com.kyra.common.session.RedisSessionStore;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.sstore.SessionStore;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.types.EventBusService;

public class ApiVerticle extends AbstractVerticle {
  protected ServiceDiscovery discovery;
  protected String ENDPOINT;
  protected Logger log = LoggerFactory.getLogger(ApiVerticle.class);
  protected Router router;
  protected SessionStore sessionStore;

  @Override
  public void start() throws Exception {
    super.start();
    discovery = ServiceDiscovery.create(vertx);
    ENDPOINT = config().getString("endpoint", "/");
    sessionStore = new RedisSessionStore();
  }

  public void launchHttpServer(String name, int port, Handler<AsyncResult<Void>> handler) {
    router = Router.router(vertx);
    router.route().handler(LoggerHandler.create());
    router.route().handler(BodyHandler.create());
    vertx.createHttpServer()
      .requestHandler(router)
      .listen(port, ar -> {
        if (ar.failed()) {
          handler.handle(Future.failedFuture(ar.cause()));
          throw new RuntimeException(String.format("Unable to launch http server on port %n", port), ar.cause());
        }

        log.info(String.format("%s http server successfully launch on port %d", name, port));
        handler.handle(Future.succeededFuture());
      });
  }

  public void publish(String name, String address, Class<?> serviceClass, Handler<AsyncResult<Void>> handler) {
    Record record = EventBusService.createRecord(name, address, serviceClass);
    publish(record, handler);
  }

  public void publish(Record record, Handler<AsyncResult<Void>> handler) {
    if (discovery == null) {
      try {
        start();
      } catch (Exception e) {
        throw new RuntimeException("Cannot create discovery service");
      }
    }

    discovery.publish(record, ar -> handler.handle(null));
  }
}