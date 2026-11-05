package ru.ticket.svg;

import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import ru.ticket.dto.TextElement;
import ru.ticket.text.AwtTextLayoutService;
import ru.ticket.text.richtext.*;

/** Convert draw.io HTML labels to ordinary SVG text before Batik sees the document. */
final class DrawioLabelNormalizer {
  private static final String SVG="http://www.w3.org/2000/svg";
  private static final AwtTextLayoutService MEASURE=new AwtTextLayoutService();
  private static final RichTextParser PARSER=new RichTextParser();
  private static final TableLayoutService TABLES=new TableLayoutService(MEASURE);
  private static final RichTextSvgRenderer RENDERER=new RichTextSvgRenderer(PARSER,MEASURE,TABLES,new TableSvgRenderer(TABLES,MEASURE));
  private DrawioLabelNormalizer(){}
  static String normalize(String svg){
    var doc=Jsoup.parse(svg,"",Parser.xmlParser());doc.outputSettings().prettyPrint(false);
    var root=doc.selectFirst("svg");if(root==null)return svg;
    double pageWidth=numeric(root.attr("width"),1000),pageHeight=numeric(root.attr("height"),1000);
    for(var foreign:new ArrayList<>(doc.select("foreignObject"))){
      Element container=foreign.children().first();if(container==null){foreign.remove();continue;}
      Map<String,String> box=style(container.attr("style")),inherited=new LinkedHashMap<>();
      Element content=container;
      for(var candidate:container.getAllElements()){
        var css=style(candidate.attr("style"));inherited.putAll(css);
        if(css.containsKey("font-size")&&numeric(css.get("font-size"),0)>0&&!candidate.text().isBlank()){content=candidate;break;}
      }
      // Computed label style follows the ancestor chain, not unrelated descendant siblings.
      inherited.clear();var ancestors=new ArrayList<Element>();for(Element current=content;current!=null&&current!=foreign;current=current.parent())ancestors.add(current);
      Collections.reverse(ancestors);for(var ancestor:ancestors)inherited.putAll(style(ancestor.attr("style")));
      double specifiedFont=numeric(inherited.get("font-size"),12),fontSize=specifiedFont>0?Math.min(512,specifiedFont):12;
      double width=numeric(box.get("width"),numeric(foreign.attr("width"),pageWidth));if(width<2)width=pageWidth;
      double height=numeric(box.get("height"),numeric(foreign.attr("height"),pageHeight));
      double x=numeric(foreign.attr("x"),0)+numeric(box.get("margin-left"),0)+numeric(box.get("padding-left"),0);
      double y=numeric(foreign.attr("y"),0)+numeric(box.get("margin-top"),0)+numeric(box.get("padding-top"),0);
      var text=new TextElement();text.fontSize=fontSize;text.width=width;text.height=Math.max(1,height);text.padding=new ru.ticket.dto.Padding();
      text.fontFamily=inherited.getOrDefault("font-family","Arial").replace("\"","").replace("'","");
      text.fill=SvgSanitizer.printColor(inherited.getOrDefault("color","#000000"));
      text.lineHeight=numeric(inherited.get("line-height"),1.2);if(text.lineHeight>4)text.lineHeight/=fontSize;if(text.lineHeight<=0)text.lineHeight=1.2;
      text.align=inherited.getOrDefault("text-align",box.getOrDefault("justify-content","").contains("center")?"center":"left");
      for(var descendant:content.getAllElements())if(descendant.hasAttr("style"))descendant.attr("style",SvgSanitizer.printColor(descendant.attr("style")));
      String inline="font-size:"+fontSize+"px;font-family:"+ru.ticket.util.XmlEscape.escape(text.fontFamily)+";color:"+text.fill+";font-weight:"+inherited.getOrDefault("font-weight","normal")+";font-style:"+inherited.getOrDefault("font-style","normal")+";";
      text.html="<div style=\""+inline+"\">"+content.html()+"</div>";
      var blocks=PARSER.parse(text.html);double textHeight=RichTextLayout.layout(blocks,width,(int)Math.round(fontSize),text.fontFamily,text.lineHeight,MEASURE,TABLES).totalHeight();
      if(box.getOrDefault("align-items","").contains("center")){y+=(height-textHeight)/2;text.height=Math.max(text.height,textHeight);}
      else if(box.getOrDefault("align-items","").contains("end")){y+=height-textHeight;text.height=Math.max(text.height,textHeight);}
      StringBuilder output=new StringBuilder();RENDERER.render(output,text);
      var fragment=Jsoup.parse("<svg xmlns=\""+SVG+"\"><g transform=\"translate("+x+","+y+")\">"+output+"</g></svg>","",Parser.xmlParser()).selectFirst("g");
      if(fragment==null)continue;
      if(foreign.parent()!=null&&foreign.parent().tagName().equalsIgnoreCase("switch"))foreign.parent().replaceWith(fragment);else foreign.replaceWith(fragment);
    }
    return doc.outerHtml();
  }
  private static Map<String,String> style(String css){var result=new LinkedHashMap<String,String>();for(String declaration:css.split(";")){int colon=declaration.indexOf(':');if(colon>0)result.put(declaration.substring(0,colon).trim().toLowerCase(Locale.ROOT),declaration.substring(colon+1).trim());}return result;}
  private static double numeric(String value,double fallback){if(value==null||value.isBlank()||value.endsWith("%"))return fallback;try{double n=Double.parseDouble(value.trim().replaceFirst("px$", ""));return Double.isFinite(n)?n:fallback;}catch(NumberFormatException error){return fallback;}}
}
