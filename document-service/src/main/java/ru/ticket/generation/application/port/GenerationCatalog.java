package ru.ticket.generation.application.port;

import java.util.*;
import ru.ticket.dto.Slide;
import ru.ticket.generation.application.dto.GenerationRequest;
import ru.ticket.schema.domain.FieldDef;
import ru.ticket.template.domain.BindingDto;

/** Ownership-aware read port. The use case does not depend on JPA entities. */
public interface GenerationCatalog {
  record Template(
      List<Slide> slides, String schemaId, List<FieldDef> fields, List<BindingDto> bindings) {}

  record Model(String schemaId, String name, Map<String, Object> values) {}

  Template template(String userId, GenerationRequest request);

  Map<String, Model> models(String userId, Collection<String> ids);
}
