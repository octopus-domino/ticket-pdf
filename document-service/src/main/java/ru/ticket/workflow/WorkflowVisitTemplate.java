package ru.ticket.workflow;

import java.util.*;
import ru.ticket.dto.*;
import ru.ticket.generation.application.dto.GenerationSource;
import ru.ticket.generation.application.port.GenerationCatalog;
import ru.ticket.schema.domain.FieldDef;

/** The verification card is a native constructor page, filled by the same model as the document. */
final class WorkflowVisitTemplate {
  private WorkflowVisitTemplate() {}
  static GenerationSource append(GenerationSource source, WorkflowDefinition.Attendance labels) {
    if (source.template().fields().stream().anyMatch(field -> field.path().equals("visit_url")))
      throw ru.ticket.common.ApiException.badRequest("visit_url is reserved for attendance verification");
    var page = new Slide(); page.id = "attendance-check"; page.width = 397; page.height = 559; page.format = "a6";
    var heading = new TextElement(); heading.type = "text"; heading.id = "visit-title"; heading.x = 24; heading.y = 32;
    heading.width = 349; heading.height = 80; heading.fontSize = 24; heading.text = labels.title(); heading.fontFamily = "DejaVu Sans";
    var qr = new QrElement(); qr.type = "qr"; qr.id = "visit-qr"; qr.x = 96; qr.y = 136; qr.width = 205; qr.height = 205; qr.text = "{{visit_url}}";
    var instructions = new TextElement(); instructions.type = "text"; instructions.id = "visit-instructions";
    instructions.x = 24; instructions.y = 370; instructions.width = 349; instructions.height = 150;
    instructions.fontSize = 14; instructions.fontFamily = "DejaVu Sans"; instructions.text = labels.instructions();
    page.elements.addAll(List.of(heading, qr, instructions));
    var pages = new ArrayList<>(source.template().slides()); pages.add(page);
    var fields = new ArrayList<>(source.template().fields()); fields.add(new FieldDef("visit_url", "string", false, "Verification link", null));
    return new GenerationSource(new GenerationCatalog.Template(pages, source.template().schemaId(), fields, source.template().bindings()), source.models());
  }
}
