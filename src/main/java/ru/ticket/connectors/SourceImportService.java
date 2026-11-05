package ru.ticket.connectors;

import com.fasterxml.jackson.databind.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.clientapi.SecretCipher;
import ru.ticket.common.ApiException;
import ru.ticket.datamodel.domain.DataModelEntity;
import ru.ticket.datamodel.repository.DataModelRepository;
import ru.ticket.schema.repository.SchemaRepository;
import ru.ticket.schema.domain.SchemaEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import ru.ticket.generation.domain.ModelValues;

@Service
public class SourceImportService {
  private static final Set<String> PROVIDERS =
      Set.of("one-c-odata", "one-c-http", "bitrix24", "amocrm", "json");
  private static final Set<Integer> SYNC_INTERVALS = Set.of(0, 15, 60, 1440);
  private final SourceConnectionRepository sources;
  private final SourceHttpClient http;
  private final SecretCipher cipher;
  private final ObjectMapper mapper;
  private final SchemaRepository schemas;
  private final DataModelRepository models;

  private record Cached(
      String owner, String source, Instant expires,
      List<Map<String, Object>> rows, boolean truncated) {}
  private final Map<String, Cached> previews = new ConcurrentHashMap<>();

  public SourceImportService(
      SourceConnectionRepository sources, SourceHttpClient http, SecretCipher cipher,
      ObjectMapper mapper, SchemaRepository schemas, DataModelRepository models) {
    this.sources = sources;
    this.http = http;
    this.cipher = cipher;
    this.mapper = mapper;
    this.schemas = schemas;
    this.models = models;
  }

  public record View(
      String id, String name, String provider, String origin, String schemaId,
      String keyPath, String namePath, Map<String, String> mapping,
      int autoSyncMinutes, Instant lastSyncAt, String lastStatus) {}

  public record Preview(String token, List<Map<String, Object>> rows, boolean truncated) {}

  public record ImportRequest(
      @NotBlank String token,
      @NotBlank String schemaId,
      @NotBlank @Size(max = 1000) String keyPath,
      @Size(max = 1000) String namePath,
      @NotEmpty @Size(max = 500) Map<String, String> mapping,
      @Size(max = 10000) Map<Integer, Map<String, Object>> overrides,
      int autoSyncMinutes) {}


  private record MappedRows(
      List<Map<String, Object>> values, List<String> externalIds) {}


  public List<View> list(String owner) {
    return sources.findByUserIdOrderByName(owner).stream().map(this::view).toList();
  }

  public View create(
      String owner, String name, String provider, String url,
      String auth, String username, String password) {
    if (name == null || name.isBlank() || name.length() > 200 || !PROVIDERS.contains(provider)) {
      throw ApiException.badRequest("Invalid source settings");
    }
    if (sources.findByUserIdOrderByName(owner).size()>=10) {
      throw ApiException.badRequest("Maximum 10 connections");
    }
    var source = new SourceConnection();
    source.userId = owner;
    source.name = name.trim();
    source.provider = provider;
    source.origin = http.validate(url);
    String authorization = switch (auth) {
      case "none" -> "";
      case "basic" -> "Basic "+Base64.getEncoder().encodeToString((username+":"+password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      case "bearer" -> "Bearer "+password;
      default -> throw ApiException.badRequest("Invalid source authentication");
    };
    source.encryptedUrl = cipher.encrypt(url);
    source.encryptedAuth = cipher.encrypt(authorization);
    return view(sources.save(source));
  }

  public void delete(String owner, String id) {
    sources.delete(owned(owner, id));
    previews.entrySet().removeIf(e->e.getValue().owner.equals(owner) && e.getValue().source.equals(id));
  }
  /** Preview tokens are owner-bound, expire after five minutes, and keep response rows in RAM. */

  public synchronized Preview preview(String owner, String id) {
    var source = owned(owner, id);
    previews.entrySet().removeIf(e->!e.getValue().expires.isAfter(Instant.now()) || e.getValue().owner.equals(owner) && e.getValue().source.equals(id));
    if (previews.size()>=8) {
      throw ApiException.conflict("Import preview capacity reached; try again shortly");
    }
    JsonNode root = http.fetch(cipher.decrypt(source.encryptedUrl), cipher.decrypt(source.encryptedAuth));
    JsonNode array = records(root);
    if (!array.isArray() || array.isEmpty()) {
      throw ApiException.badRequest("No JSON records found in source");
    }
    if (array.size()>10000) {
      throw ApiException.badRequest("Source has more than 10000 records; add pagination or a filter");
    }
    var rows = new ArrayList<Map<String, Object>>();
    for (JsonNode row:array) {
      if (!row.isObject() || row.size()>500 || !safe(row, 0)) {
        throw ApiException.badRequest("Invalid source record");
      }
      rows.add(mapper.convertValue(row, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>(){}));
    }
    String token = UUID.randomUUID().toString();
    boolean truncated = root.has("@odata.nextLink") || root.has("odata.nextLink") || root.has("next") || root.path("_links").has("next");
    previews.put(token, new Cached(owner, id, Instant.now().plusSeconds(300), rows, truncated));
    return new Preview(token, rows, truncated);
  }
  /** Provider envelopes differ; normalize only their record-array wrapper here. */

  static JsonNode records(JsonNode root) {
    if (root.isArray()) {
      return root;
    }
    for (String key : List.of("value", "items", "result")) {
      if (root.path(key).isArray()) return root.path(key);
    }
    if (root.path("d").path("results").isArray()) {
      return root.path("d").path("results");
    }
    if (root.path("result").path("items").isArray()) {
      return root.path("result").path("items");
    }
    for (JsonNode value : root.path("_embedded")) {
      if (value.isArray()) return value;
    }
    return root;
  }

  private static boolean safe(JsonNode node, int depth) {
    if (depth>20) {
      return false;
    }
    var fields = node.fieldNames();
    while (fields.hasNext()) {
      if (Set.of("__proto__", "constructor", "prototype").contains(fields.next())) return false;
    }
    if (node.isContainerNode()) {
      for (JsonNode child : node) {
        if (!safe(child, depth + 1)) return false;
      }
    }
    return true;
  }
  @Transactional

  public Map<String, Integer> apply(String owner, String id, ImportRequest request) {
    // The same row lock serializes manual imports and scheduled synchronization.
    var source = sources.lockOwned(id, owner).orElseThrow(()->ApiException.notFound("Connection"));
    var cached = previews.get(request.token());
    if (cached == null || !cached.owner.equals(owner) || !cached.source.equals(id)
        || !cached.expires.isAfter(Instant.now())) {
      throw ApiException.badRequest("Preview expired; load records again");
    }
    var schema = schemas.findByIdAndUserId(request.schemaId(), owner).orElseThrow(()->ApiException.notFound("Schema"));
    if (cached.truncated && request.autoSyncMinutes()>0) {
      throw ApiException.badRequest("Automatic sync requires a complete source page; filter the source or configure its export endpoint");
    }
    if (request.mapping() == null || request.mapping().isEmpty() || request.mapping().size() > 500
        || request.keyPath() == null || request.keyPath().isBlank()
        || !SYNC_INTERVALS.contains(request.autoSyncMinutes())) {
      throw ApiException.badRequest("Choose a field mapping and a stable external ID");
    }
    Set<String> allowed = new HashSet<>();
    for (var field : schema.getFields()) allowed.add(field.path());
    if (!allowed.containsAll(request.mapping().keySet())) {
      throw ApiException.badRequest("Mapping contains unknown schema fields");
    }
    // Validate every row before writing any models; a bad row must not leave a partial import.
    MappedRows prepared = prepareRows(cached, request, schema, allowed);
    Map<String, Integer> result = saveModels(owner, source, cached, request, prepared);
    source.mapping = new LinkedHashMap<>(request.mapping());
    source.schemaId = request.schemaId();
    source.keyPath = request.keyPath();
    source.namePath = request.namePath();
    source.autoSyncMinutes = request.autoSyncMinutes();
    source.lastSyncAt = Instant.now();
    source.lastStatus = "success";
    sources.save(source);
    return result;
  }

  private MappedRows prepareRows(
      Cached cached, ImportRequest request, SchemaEntity schema, Set<String> allowed) {
    var ids = new HashSet<String>();
    var values = new ArrayList<Map<String, Object>>();
    var externalIds = new ArrayList<String>();
    for (int i = 0;i<cached.rows.size();i++) {
      var row = cached.rows.get(i);
      Object raw = get(row, request.keyPath());
      String external = raw == null?"":raw.toString();
      if (external.isBlank() || external.length()>1000 || !ids.add(external)) {
        throw ApiException.badRequest("Row "+(i+1)+": missing or duplicate external ID");
      }
      var mapped = new LinkedHashMap<String, Object>();
      for (var mapping : request.mapping().entrySet()) {
        mapped.put(mapping.getKey(), get(row, mapping.getValue()));
      }
      if (request.overrides() != null && request.overrides().containsKey(i)) {
        var patch = request.overrides().get(i);
        if (patch.size()>500 || !allowed.containsAll(patch.keySet()) || !safe(mapper.valueToTree(patch), 0)) {
          throw ApiException.badRequest("Unknown or unsafe override field");
        }
        mapped.putAll(patch);
      }
      for (var field:schema.getFields()) {
        Object value = mapped.get(field.path());
        if (value instanceof String text) {
          if (field.type().equals("date") && text.matches("\\d{4}-\\d{2}-\\d{2}T.*")) {
            mapped.put(field.path(), text.substring(0, 10));
          }
          if (field.type().equals("number") && !text.isBlank()) {
            try {
              if (text.length()>1000) {
                throw new NumberFormatException();
              }
              mapped.put(field.path(), new java.math.BigDecimal(text.trim().replace(',', '.')));
            } catch (NumberFormatException error){
              throw ApiException.badRequest("Row "+(i+1)+": invalid number in "+field.path());
            }
          }
          if (field.type().equals("boolean") && Set.of("true", "false").contains(text)) {
            mapped.put(field.path(), Boolean.valueOf(text));
          }
        }
      }
      try {
        values.add(ModelValues.validate(mapped, schema.getFields()));
      } catch (IllegalArgumentException error){
        throw ApiException.badRequest("Row "+(i+1)+": "+error.getMessage());
      }
      externalIds.add(external);
    }
    return new MappedRows(values, externalIds);
  }


  private Map<String, Integer> saveModels(
      String owner, SourceConnection source, Cached cached,
      ImportRequest request, MappedRows prepared) {
    var values = prepared.values();
    var externalIds = prepared.externalIds();
    // The source keeps stable external-ID links, so repeated imports update existing models.
    if (source.links == null) {
      source.links = new LinkedHashMap<>();
    }
    int created = 0, updated = 0;
    for (int i = 0;i<values.size();i++) {
      String link = SecretCipher.hash(request.schemaId()+":"+externalIds.get(i));
      var existing = Optional.ofNullable(source.links.get(link)).flatMap(modelId->models.findByIdAndUserId(modelId, owner));
      Object rawName = request.namePath() == null?null:get(cached.rows.get(i), request.namePath());
      String name = rawName == null?source.name+" "+externalIds.get(i):rawName.toString();
      if (name.length()>200) {
        name = name.substring(0, 200);
      }
      DataModelEntity model;
      if (existing.isPresent()){
        model = existing.get();
        model.rename(name);
        model.replaceValues(values.get(i));
        updated++;
      } else {
        model = new DataModelEntity(owner, request.schemaId(), name, values.get(i));
        created++;
      }
      models.save(model);
      source.links.put(link, model.getId());
    }
    if (source.links.size()>10000) {
      throw ApiException.badRequest("Connection exceeds 10000 linked records");
    }
    return Map.of("created", created, "updated", updated);
  }

  @Transactional

  public void sync(String owner, String id) {
    var source = sources.lockOwned(id, owner).orElseThrow(()->ApiException.notFound("Connection"));
    if (source.autoSyncMinutes<=0) {
      return;
    }
    var preview = preview(owner, id);
    apply(owner, id, new ImportRequest(preview.token(), source.schemaId, source.keyPath, source.namePath, source.mapping, Map.of(), source.autoSyncMinutes));
  }
  /** Exact keys take precedence over dotted traversal (OData fields can contain dots). */

  static Object get(Map<String, Object> row, String path) {
    if (path == null || path.length()>1000) {
      return null;
    }
    if (row.containsKey(path)) {
      return row.get(path);
    }
    Object current = row;
    for (String part:path.split("\\.")) {
      if (Set.of("__proto__", "constructor", "prototype").contains(part)) {
        throw ApiException.badRequest("Unsafe source field");
      }
      if (!(current instanceof Map<?, ?> map)) {
        return null;
      }
      current = map.get(part);
    }
    return current;
  }

  public SourceConnection owned(String owner, String id){
    return sources.findByIdAndUserId(id, owner).orElseThrow(()->ApiException.notFound("Connection"));
  }

  private View view(SourceConnection s){
    return new View(s.id, s.name, s.provider, s.origin, s.schemaId, s.keyPath, s.namePath, s.mapping, s.autoSyncMinutes, s.lastSyncAt, s.lastStatus);
  }
}
