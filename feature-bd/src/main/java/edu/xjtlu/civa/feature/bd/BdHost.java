package edu.xjtlu.civa.feature.bd;

import edu.xjtlu.civa.core.data.ApiCalls;
import edu.xjtlu.civa.core.data.ApiProvider;
import edu.xjtlu.civa.core.data.SessionStore;
import edu.xjtlu.civa.core.ui.NavHost;

/** App-owned dependency boundary. BD never reaches into another feature. */
public interface BdHost extends NavHost {
  ApiProvider api();

  ApiCalls calls();

  SessionStore sessions();
}
