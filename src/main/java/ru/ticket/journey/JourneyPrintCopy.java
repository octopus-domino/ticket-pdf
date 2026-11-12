package ru.ticket.journey;

/** Print-only copy follows the author's publication language, independently of the viewer UI. */
final class JourneyPrintCopy {
  static String qr(JourneyService.View view) {
    String language = view.data().getOrDefault("locale", "ru").split("[-_]")[0];
    boolean certificate = view.kind() == JourneyService.Kind.CERTIFICATE;
    return switch (language) {
      case "ru" -> certificate ? "Проверка сертификата по QR" : "Актуальная страница по QR";
      case "zh" -> certificate ? "扫码验证证书" : "扫码查看最新页面";
      case "pl" -> certificate ? "Sprawdź certyfikat przez QR" : "Aktualna strona przez QR";
      case "it" -> certificate ? "Verifica il certificato con QR" : "Pagina aggiornata tramite QR";
      case "es" -> certificate ? "Verifica el certificado por QR" : "Página actualizada por QR";
      case "nl" -> certificate ? "Certificaat controleren via QR" : "Actuele pagina via QR";
      case "fr" -> certificate ? "Vérifier le certificat par QR" : "Page à jour via QR";
      case "de" -> certificate ? "Zertifikat per QR prüfen" : "Aktuelle Seite per QR";
      default -> certificate ? "Verify the certificate by QR" : "Current page by QR";
    };
  }
}
