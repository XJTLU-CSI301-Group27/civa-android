package edu.xjtlu.civa.server.web;

import edu.xjtlu.civa.server.api.MediaApi;
import edu.xjtlu.civa.server.api.model.*;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Explicit 501 placeholders for the generated contracts that do not yet have implementations. */
@RestController
class UnimplementedController implements MediaApi {

  @Override
  public ResponseEntity<Resource> getMediaContent(UUID mediaId) {
    throw new NotImplemented();
  }

  @Override
  public ResponseEntity<Media> uploadMedia(
      UUID key, MultipartFile file, MediaScope scope, UUID householdId) {
    throw new NotImplemented();
  }

  static final class NotImplemented extends RuntimeException {}
}
