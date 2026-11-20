package edu.xjtlu.civa.server.web;

/** Shared page-size rule for every paged endpoint. */
public final class Paging {
  private Paging() {}

  public static void check(int page, int size) {
    if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid pagination");
  }
}
