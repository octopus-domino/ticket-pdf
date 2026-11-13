package ru.ticket.workflow;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import ru.ticket.generation.application.dto.GenerationSource;
import ru.ticket.generation.application.port.GenerationCatalog;
import ru.ticket.generation.domain.TemplateMaterializer;

@DataJpaTest
class WorkflowVisitTest {
  @Autowired WorkflowVisitRepository repository;
  @Test void ownerChecksAndVisitsAreStoredIdempotentlyWithoutPublicNotesOrCheckerIdentity() {
    var service = new WorkflowVisitService(repository, "https://www.codoc.site");
    var workflow = new WorkflowEntity(); workflow.ownerId = "owner"; workflow.slug = "course"; workflow.publishedRevision = 2;
    var visit = service.draft(workflow, "Course"); service.issued(visit, new byte[] {1, 2, 3});
    assertThat(service.status(visit.id, null).canCheck()).isFalse();
    assertThatThrownBy(() -> service.check(visit.id, "stranger", new WorkflowVisitService.Check(true, null)))
        .isInstanceOf(ru.ticket.common.ApiException.class);
    var checked = service.check(visit.id, "owner", new WorkflowVisitService.Check(false, "Checked on arrival"));
    assertThat(checked.checkedAt()).isNotNull(); assertThat(checked.visitedAt()).isNull();
    var attended = service.check(visit.id, "owner", new WorkflowVisitService.Check(true, "Admitted"));
    assertThat(attended.checkedAt()).isEqualTo(checked.checkedAt()); assertThat(attended.visitedAt()).isNotNull();
    var duplicate = service.check(visit.id, "owner", new WorkflowVisitService.Check(true, "Repeated scan"));
    assertThat(duplicate).isEqualTo(attended);
    var publicStatus = service.status(visit.id, null);
    assertThat(publicStatus.visitedAt()).isEqualTo(attended.visitedAt());
    assertThat(publicStatus.note()).isNull(); assertThat(publicStatus.checkedBy()).isNull();
    assertThat(repository.findById(visit.id).orElseThrow().documentSha256).hasSize(64);
  }
  @Test void verificationCardIsANativeTemplatePageWithAnActualModelBoundQr() {
    var template = new GenerationCatalog.Template(List.of(new ru.ticket.dto.Slide()), null, List.of(), List.of());
    var source = WorkflowVisitTemplate.append(new GenerationSource(template, Map.of()), new WorkflowDefinition.Attendance("Check", "Show QR"));
    var pages = TemplateMaterializer.fill(source.template(), Map.of("visit_url", "https://www.codoc.site/visits/123"));
    assertThat(pages).hasSize(2);
    assertThat(pages.get(1).elements).anyMatch(element -> element instanceof ru.ticket.dto.QrElement qr && qr.text.equals("https://www.codoc.site/visits/123"));
    assertThat(template.slides()).hasSize(1);
  }
}
