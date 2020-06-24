package com.kyra.account.service.impl;

import com.kyra.account.service.AccountService;
import com.kyra.common.pg.Pg;
import com.kyra.common.pg.PgResult;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;

public class AccountServiceImpl implements AccountService {
  @Override
  public void register(String email, String firstName, String lastName, String password, Handler<AsyncResult<JsonObject>> handler) {
    String query = "INSERT INTO account.user(email, \"firstName\", \"lastName\", password) " +
      "VALUES ($1, $2, $3, $4) RETURNING email, \"firstName\", \"lastName\";";
    Tuple params = Tuple.of(email, firstName, lastName, password);
    Pg.getInstance().preparedQuery(query, params, PgResult.uniqueJsonResult(handler));
  }

  @Override
  public void findUser(String email, Handler<AsyncResult<JsonObject>> handler) {
    String query = "SELECT email FROM account.user WHERE email = $1";
    Pg.getInstance().preparedQuery(query, Tuple.of(email), PgResult.uniqueJsonResult(handler));
  }
}
