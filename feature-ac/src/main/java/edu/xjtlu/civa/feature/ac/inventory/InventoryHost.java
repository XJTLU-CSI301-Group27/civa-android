package edu.xjtlu.civa.feature.ac.inventory;

import edu.xjtlu.civa.core.data.ApiCalls;
import edu.xjtlu.civa.core.data.ApiProvider;
import edu.xjtlu.civa.core.data.SessionStore;
import edu.xjtlu.civa.core.ui.NavHost;

/** Candidate app-to-inventory assembly seam, documented in IC-20261122-C-UI-01. */
public interface InventoryHost extends NavHost {
  ApiProvider api();

  ApiCalls calls();

  SessionStore sessions();
}
