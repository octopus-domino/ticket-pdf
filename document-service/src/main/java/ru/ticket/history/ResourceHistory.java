package ru.ticket.history;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import ru.ticket.common.ApiException;
import ru.ticket.clientapi.SecretCipher;

@Service
public class ResourceHistory {
  private final ResourceRevisionRepository repository;
  private final ObjectMapper mapper;

  public ResourceHistory(ResourceRevisionRepository repository, ObjectMapper mapper){
    this.repository = repository;
    this.mapper = mapper;
  }
  @Transactional public void capture(String owner, String kind, String resourceId, Object value) {
    var tree = mapper.valueToTree(value);
    var canonical = tree.deepCopy();
    if (canonical instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
      object.remove(List.of("id", "updatedAt", "createdAt"));
    }
    String payload = tree.toString(), hash = SecretCipher.hash(canonical.toString());
    var previous = repository.findFirstByUserIdAndKindAndResourceIdOrderByCreatedAtDesc(owner, kind, resourceId);
    if (previous.isPresent() && previous.get().contentHash.equals(hash)) {
      return;
    }
    int bytes = payload.getBytes(StandardCharsets.UTF_8).length;
    if (bytes>20*1024*1024) {
      throw ApiException.badRequest("Version exceeds 20 MB");
    }
    var revision = new ResourceRevision();
    revision.userId = owner;
    revision.kind = kind;
    revision.resourceId = resourceId;
    revision.payload = payload;
    revision.contentHash = hash;
    revision.payloadBytes = bytes;
    repository.saveAndFlush(revision);
    var versions = repository.findByUserIdAndKindAndResourceIdOrderByCreatedAtDesc(owner, kind, resourceId);
    for (var old:versions.stream().skip(20).toList())repository.deleteById(old.getId());
    while (repository.totalBytes(owner)>50L*1024*1024){
      var oldest = repository.findByUserIdOrderByCreatedAtAsc(owner, org.springframework.data.domain.PageRequest.of(0, 1));
      if (oldest.isEmpty()) {
        break;
      }
      repository.delete(oldest.get(0));
      repository.flush();
    }
  }

  public List<ResourceRevisionRepository.Header> list(String owner, String kind, String resource){
    checkKind(kind);
    return repository.findByUserIdAndKindAndResourceIdOrderByCreatedAtDesc(owner, kind, resource);
  }

  public JsonNode read(String owner, String kind, String resource, String revision){
    checkKind(kind);
    var record = repository.findByIdAndUserIdAndKindAndResourceId(revision, owner, kind, resource).orElseThrow(()->ApiException.notFound("Version"));
    try{
      return mapper.readTree(record.payload);
    } catch (java.io.IOException error){
      throw new IllegalStateException("Invalid version snapshot", error);
    }
  }

  private void checkKind(String kind){
    if (!Set.of("template", "document").contains(kind)) {
      throw ApiException.badRequest("Unknown version kind");
    }
  }
  @Scheduled(fixedDelay = 3600000, initialDelay = 600000) @Transactional public void cleanup(){
    repository.deleteByCreatedAtBefore(Instant.now().minusSeconds(30L*86400));
  }
}
