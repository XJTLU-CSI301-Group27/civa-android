package edu.xjtlu.civa.server.web;

/**
 * A business failure identified by a contract error code.
 *
 * <p>Lives outside any single business package so a controller never has to depend on another
 * package's exception type; one advice maps the code to an HTTP status.
 */
public class ApiFailure extends RuntimeException {
  public final String code;

  public ApiFailure(String code) {
    super(code);
    this.code = code;
  }
}
