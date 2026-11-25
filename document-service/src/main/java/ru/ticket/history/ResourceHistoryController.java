package ru.ticket.history;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.ticket.auth.security.UserIdPrincipal;
import ru.ticket.template.service.TemplateService;
import ru.ticket.template.dto.TemplateDtos.SaveTemplateRequest;
import ru.ticket.workspace.service.DocumentService;
import ru.ticket.workspace.dto.WorkspaceDtos.CreateDocumentRequest;

@RestController @RequestMapping("/api/history/{kind}/{resource}")
public class ResourceHistoryController {
  private final ResourceHistory history;
  private final TemplateService templates;
  private final DocumentService documents;
  private final ObjectMapper mapper;
  private final ru.ticket.schema.repository.SchemaRepository schemas;

  public ResourceHistoryController(ResourceHistory history, TemplateService templates, DocumentService documents, ObjectMapper mapper, ru.ticket.schema.repository.SchemaRepository schemas){
    this.history = history;
    this.templates = templates;
    this.documents = documents;
    this.mapper = mapper;
    this.schemas = schemas;
  }
  @GetMapping public List<ResourceRevisionRepository.Header> list(@AuthenticationPrincipal UserIdPrincipal me, @PathVariable String kind, @PathVariable String resource){
    return history.list(me.userId(), kind, resource);
  }
  @PostMapping("/{revision}/copy") public Map<String, String> restore(@AuthenticationPrincipal UserIdPrincipal me, @PathVariable String kind, @PathVariable String resource, @PathVariable String revision){
    var snapshot = history.read(me.userId(), kind, resource, revision);
    String name = snapshot.path("name").asText("Restored document");
    if (name.length()>185) {
      name = name.substring(0, 185);
    }
    name+=" · restored";
    if (kind.equals("template")){
      String schema = snapshot.path("schemaId").isTextual()?snapshot.path("schemaId").asText():null;
      if (schema != null && !schemas.existsByIdAndUserId(schema, me.userId())) {
        schema = null;
      }
      var slides = mapper.convertValue(snapshot.path("slides"), new com.fasterxml.jackson.core.type.TypeReference<List<ru.ticket.dto.Slide>>(){}
      );
      var bindings = mapper.convertValue(snapshot.path("bindings"), new com.fasterxml.jackson.core.type.TypeReference<List<ru.ticket.template.domain.BindingDto>>(){}
      );
      var copy = templates.create(me.userId(), new SaveTemplateRequest(schema, name, slides, bindings, null));
      return Map.of("kind", kind, "id", copy.id());
    }
    var copy = documents.create(me.userId(), new CreateDocumentRequest(name, null));
    documents.save(me.userId(), copy.id(), snapshot.path("content").asText("{}"));
    return Map.of("kind", kind, "id", copy.id());
  }
}
