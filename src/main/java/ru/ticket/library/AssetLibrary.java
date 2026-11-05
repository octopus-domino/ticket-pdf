package ru.ticket.library;

import jakarta.persistence.*;
import java.util.*;

@Entity @Table(name="asset_libraries")
public class AssetLibrary {
  @Id public String id=UUID.randomUUID().toString();
  @Column(name="user_id",nullable=false) public String userId;
  @Column(nullable=false,length=200) public String name;
  @Column(nullable=false) public long revision;
  @Lob @Column(nullable=false) public String payload="[]";
  @Lob @Column(nullable=false) public String members="{}";
}
