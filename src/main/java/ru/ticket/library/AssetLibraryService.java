package ru.ticket.library;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticket.auth.repository.UserRepository;
import ru.ticket.common.ApiException;

/** Explicit, versioned library exchange; browser assets are uploaded only on user request. */
@Service
public class AssetLibraryService {
  private static final int MAX_ITEMS = 500;
  private static final int MAX_PAYLOAD_BYTES = 10 * 1024 * 1024;
  private static final Set<String> ITEM_KINDS =
      Set.of("folder", "stamp", "style", "symbol", "template", "brand");
  private static final Set<String> UNSAFE_KEYS = Set.of("__proto__", "prototype", "constructor");

  private final AssetLibraryRepository repo;
  private final UserRepository users;
  private final ObjectMapper mapper;

  public AssetLibraryService(
      AssetLibraryRepository repo, UserRepository users, ObjectMapper mapper) {
    this.repo = repo;
    this.users = users;
    this.mapper = mapper;
  }

  public List<Map<String, Object>> list(String user) {
    return repo.findAllProjectedBy().stream()
        .filter(library -> library.getUserId().equals(user) || json(library.getMembers()).has(user))
        .map(library -> Map.<String, Object>of(
            "id", library.getId(), "name", library.getName(), "revision", library.getRevision(),
            "role", library.getUserId().equals(user)
                ? "owner" : json(library.getMembers()).path(user).asText()))
        .toList();
  }

  public Map<String, Object> read(String user, String id) {
    return view(owned(user, id, false), user);
  }

  @Transactional
  public Map<String, Object> create(String user, String name) {
    if (name == null || name.isBlank() || name.length() > 200) {
      throw ApiException.badRequest("Invalid library name");
    }
    if (repo.findByUserId(user).size() >= 10) {
      throw ApiException.badRequest("Maximum 10 libraries");
    }
    var library = new AssetLibrary();
    library.userId = user;
    library.name = name.trim();
    return view(repo.save(library), user);
  }

  @Transactional
  public Map<String, Object> save(String user, String id, long revision, JsonNode items) {
    // Hold the row lock through revision validation and write to avoid lost updates.
    var library = owned(user, id, true);
    String role = role(library, user);
    if (role.equals("viewer")) throw ApiException.forbidden("Read-only library");
    if (library.revision != revision) {
      throw ApiException.conflict("Library changed on another device; reload before saving");
    }

    Map<String, JsonNode> next = validateItems(items);
    validateFolderTree(items, next);
    if (!role.equals("owner")) {
      validateEditorChanges(items, json(library.payload), next);
    }

    library.payload = items.toString();
    library.revision++;
    return view(repo.save(library), user);
  }

  @Transactional
  public void member(String user, String id, String email, String role) {
    var library = owned(user, id, true);
    if (!library.userId.equals(user)) throw ApiException.forbidden("Owner required");
    if (!Set.of("editor", "viewer", "remove").contains(role)) {
      throw ApiException.badRequest("Invalid role");
    }
    var target = users.findByEmailIgnoreCase(email)
        .orElseThrow(() -> ApiException.notFound("Registered user"));
    if (target.getId().equals(user)) {
      throw ApiException.badRequest("Owner role cannot be changed");
    }

    var members = (ObjectNode) json(library.members);
    if (role.equals("remove")) {
      members.remove(target.getId());
    } else {
      if (members.size() >= 50 && !members.has(target.getId())) {
        throw ApiException.badRequest("Maximum 50 members");
      }
      members.put(target.getId(), role);
    }
    library.members = members.toString();
    library.revision++;
    repo.save(library);
  }

  private Map<String, JsonNode> validateItems(JsonNode items) {
    if (items == null || !items.isArray() || items.size() > MAX_ITEMS
        || items.toString().getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
      throw ApiException.badRequest("Library limit: 500 items and 10 MB");
    }
    for (var item : items) {
      validateSafeFields(item, 0, item.path("kind").asText().equals("symbol"), "");
    }

    var ids = new HashSet<String>();
    var indexed = new HashMap<String, JsonNode>();
    for (var item : items) {
      String itemId = item.path("id").asText();
      String name = item.path("name").asText();
      if (itemId.isBlank() || itemId.length() > 200 || !ids.add(itemId)
          || name.isBlank() || name.length() > 200
          || !ITEM_KINDS.contains(item.path("kind").asText())) {
        throw ApiException.badRequest("Invalid library item");
      }
      indexed.put(itemId, item);
    }
    return indexed;
  }

  private void validateFolderTree(JsonNode items, Map<String, JsonNode> indexed) {
    for (var item : items) {
      var seen = new HashSet<String>();
      JsonNode current = item;
      while (!current.path("folderId").asText().isEmpty()) {
        String parent = current.path("folderId").asText();
        if (!seen.add(parent) || seen.size() > 20 || !indexed.containsKey(parent)
            || !indexed.get(parent).path("kind").asText().equals("folder")) {
          throw ApiException.badRequest("Invalid library folder tree");
        }
        current = indexed.get(parent);
      }
    }
  }

  private void validateEditorChanges(
      JsonNode items, JsonNode previousItems, Map<String, JsonNode> next) {
    var previous = new HashMap<String, JsonNode>();
    for (var item : previousItems) previous.put(item.path("id").asText(), item);

    for (var item : items) {
      if (item.path("locked").asBoolean() && !previous.containsKey(item.path("id").asText())) {
        throw ApiException.forbidden("Only the owner can lock items");
      }
    }

    // Protection is inherited from ancestors, including when the editor deletes an item.
    for (var entry : previous.entrySet()) {
      JsonNode item = entry.getValue();
      JsonNode replacement = next.get(entry.getKey());
      boolean protectedContentChanged = isProtected(item, previous) && !item.equals(replacement);
      boolean lockChanged = replacement != null
          && item.path("locked").asBoolean() != replacement.path("locked").asBoolean();
      if (protectedContentChanged || lockChanged) {
        throw ApiException.forbidden("Only the owner can change protected assets");
      }
    }

    // Also reject inserting or moving changed assets into a protected destination folder.
    for (var item : items) {
      JsonNode parent = next.get(item.path("folderId").asText());
      while (parent != null) {
        if (parent.path("locked").asBoolean()
            && !item.equals(previous.get(item.path("id").asText()))) {
          throw ApiException.forbidden("Protected folder");
        }
        parent = next.get(parent.path("folderId").asText());
      }
    }
  }

  private boolean isProtected(JsonNode item, Map<String, JsonNode> indexed) {
    var seen = new HashSet<String>();
    JsonNode current = item;
    boolean locked = false;
    while (current != null && seen.add(current.path("id").asText())) {
      locked |= current.path("locked").asBoolean();
      current = indexed.get(current.path("folderId").asText());
    }
    return locked;
  }

  private void validateSafeFields(JsonNode node, int depth, boolean symbol, String path) {
    if (depth > 30) throw ApiException.badRequest("Library nesting is too deep");
    if (node.isObject()) {
      var fields = node.fields();
      while (fields.hasNext()) {
        var field = fields.next();
        String key = field.getKey();
        // Symbol payload.prototype is an application object, not a JS prototype assignment.
        boolean symbolPrototype = key.equals("prototype") && symbol && path.equals("payload");
        if (UNSAFE_KEYS.contains(key) && !symbolPrototype) {
          throw ApiException.badRequest("Unsafe library field");
        }
        validateSafeFields(field.getValue(), depth + 1, symbol,
            path.isEmpty() ? key : path + "." + key);
      }
    } else if (node.isArray()) {
      for (var child : node) validateSafeFields(child, depth + 1, symbol, path + "[]");
    }
  }

  private JsonNode json(String value) {
    try {
      return mapper.readTree(value);
    } catch (Exception error) {
      throw new IllegalStateException("Invalid library storage", error);
    }
  }

  private String role(AssetLibrary library, String user) {
    return library.userId.equals(user) ? "owner" : json(library.members).path(user).asText("");
  }

  private AssetLibrary owned(String user, String id, boolean lock) {
    var library = (lock ? repo.locked(id) : repo.findById(id))
        .orElseThrow(() -> ApiException.notFound("Library"));
    // Nonmembers receive 404 so the endpoint does not reveal other users' libraries.
    if (role(library, user).isEmpty()) throw ApiException.notFound("Library");
    return library;
  }

  private Map<String, Object> view(AssetLibrary library, String user) {
    return Map.of("id", library.id, "name", library.name, "revision", library.revision,
        "role", role(library, user), "items", json(library.payload));
  }
}
