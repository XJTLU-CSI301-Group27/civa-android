package edu.xjtlu.civa.server.inventory;

final class InventoryFailure extends RuntimeException {
  final String code;

  InventoryFailure(String code) {
    super(code);
    this.code = code;
  }
}
