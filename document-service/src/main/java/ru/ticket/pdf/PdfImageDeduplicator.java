package ru.ticket.pdf;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/** Reuses byte-identical images after page merging; never resamples or recompresses artwork. */
final class PdfImageDeduplicator {
  private PdfImageDeduplicator() {}
  static byte[] optimize(byte[] pdf) throws IOException {
    try(var document=Loader.loadPDF(pdf)) {
      var images=new HashMap<String,PDImageXObject>();
      var visited=Collections.newSetFromMap(new IdentityHashMap<COSDictionary,Boolean>());
      int replaced=0;
      for(var page:document.getPages())replaced+=resources(page.getResources(),images,visited,0);
      if(replaced==0)return pdf;
      var output=new ByteArrayOutputStream();document.save(output);
      byte[] result=output.toByteArray();return result.length<pdf.length?result:pdf;
    }
  }
  private static int resources(PDResources resources,Map<String,PDImageXObject> images,Set<COSDictionary> visited,int depth) throws IOException {
    if(resources==null||depth>12||!visited.add(resources.getCOSObject()))return 0;
    int replaced=0;
    var names=new ArrayList<COSName>();resources.getXObjectNames().forEach(names::add);
    for(var name:names) {
      PDXObject object=resources.getXObject(name);
      if(object instanceof PDImageXObject image) {
        String hash=fingerprint(image.getCOSObject());
        if(hash==null)continue;
        var previous=images.putIfAbsent(hash,image);
        if(previous!=null&&previous.getCOSObject()!=image.getCOSObject()){resources.put(name,previous);replaced++;}
      } else if(object instanceof PDFormXObject form)replaced+=resources(form.getResources(),images,visited,depth+1);
    }
    return replaced;
  }
  private static String fingerprint(COSBase value) throws IOException {
    try {
      var digest=MessageDigest.getInstance("SHA-256");
      append(value,digest,Collections.newSetFromMap(new IdentityHashMap<COSBase,Boolean>()),0);
      return HexFormat.of().formatHex(digest.digest());
    }catch(IllegalArgumentException unsupported){return null;}
    catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
  }
  private static void append(COSBase value,MessageDigest digest,Set<COSBase> path,int depth) throws IOException {
    if(value instanceof COSObject reference){append(reference.getObject(),digest,path,depth);return;}
    if(value==null){token(digest,"null");return;}
    if(depth>16||!path.add(value))throw new IllegalArgumentException("Cyclic image resource");
    try {
      token(digest,value.getClass().getName());
      if(value instanceof COSDictionary dictionary) {
        var keys=new ArrayList<>(dictionary.keySet());keys.sort(Comparator.comparing(COSName::getName));
        for(var key:keys)if(!key.equals(COSName.LENGTH)){token(digest,key.getName());append(dictionary.getDictionaryObject(key),digest,path,depth+1);}
        if(value instanceof COSStream stream)try(var input=stream.createRawInputStream()){
          byte[] buffer=new byte[8192];int length;long size=0;
          while((length=input.read(buffer))!=-1){size+=length;if(size>32L*1024*1024)throw new IllegalArgumentException("Large image resource");digest.update(buffer,0,length);}
        }
      } else if(value instanceof COSArray array){for(var item:array)append(item,digest,path,depth+1);}
      else if(value instanceof COSString string)digest.update(string.getBytes());
      else token(digest,value.toString());
    }finally{path.remove(value);}
  }
  private static void token(MessageDigest digest,String value){digest.update(value.getBytes(StandardCharsets.UTF_8));digest.update((byte)0);}
}
