package ru.ticket.journey.walk;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.*;
import org.jsoup.Jsoup;
import ru.ticket.mcp.CommonsMcpClient.CommonsImage;

/** A photograph is usable only together with a trustworthy source and explicit reuse metadata. */
public final class CommonsImages {
  private CommonsImages() {}
  public static List<CommonsImage> parse(JsonNode payload){
    var images=new ArrayList<CommonsImage>();
    for(var page:payload.path("query").path("pages"))for(var info:page.path("imageinfo")){
      var meta=info.path("extmetadata");
      String license=plain(meta.path("LicenseShortName").path("value").asText(""));
      String licenseUrl=meta.path("LicenseUrl").path("value").asText("").replaceFirst("^http:","https:");
      if(licenseUrl.startsWith("//"))licenseUrl="https:"+licenseUrl;
      if(licenseUrl.isBlank()&&meta.path("Copyrighted").path("value").asText().equalsIgnoreCase("false")){
        licenseUrl="https://creativecommons.org/publicdomain/mark/1.0/";if(license.isBlank())license="Public domain";
      }
      String author=plain(meta.path("Artist").path("value").asText(info.path("user").asText("")));
      String source=info.path("descriptionurl").asText("");
      String image=info.path("thumburl").asText(info.path("url").asText(""));
      if(!source.matches("https://commons\\.wikimedia\\.org/wiki/File:[^\\p{Cntrl}<>\" ]{1,1500}")||!validImage(image)
          ||!validLicense(licenseUrl)||license.isBlank()||license.length()>80||author.isBlank()||author.length()>500)continue;
      images.add(new CommonsImage(thumbnail(image),source,author,license,licenseUrl));
    }
    return List.copyOf(images);
  }
  public static boolean validLicense(String value){return value!=null&&value.matches("https://creativecommons\\.org/(licenses|publicdomain)/[a-z0-9./-]{1,120}");}
  public static boolean validImage(String value){
    try{var uri=URI.create(value);return "https".equals(uri.getScheme())&&uri.getHost()!=null&&Set.of("upload.wikimedia.org","thumb.wikimedia.org").contains(uri.getHost())&&uri.getUserInfo()==null&&uri.getPort()==-1&&uri.getRawPath().toLowerCase(Locale.ROOT).matches(".*\\.(jpg|jpeg|png)");}
    catch(RuntimeException invalid){return false;}
  }
  private static String thumbnail(String url){
    var uri=URI.create(url);String path=uri.getRawPath();
    // Wikimedia rejects non-standard manually constructed widths (for example 640px).
    // 500px is an official tier and sufficient for the bounded print collage.
    if(path.contains("/thumb/"))return url.replaceFirst("/[0-9]+px-([^/]+)$", "/500px-$1");
    if(!path.matches("/wikipedia/commons/[0-9a-f]/[0-9a-f]{2}/.+"))return url;
    String name=path.substring(path.lastIndexOf('/')+1);
    return "https://"+uri.getHost()+path.replace("/commons/","/commons/thumb/")+"/500px-"+name;
  }
  private static String plain(String text){return Jsoup.parse(text).text();}
}
