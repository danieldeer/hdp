package com.hirschdaniel.hdp;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PacketSpecification {
  public String name;
  public Integer totalLength; // make optional
  public List<Field> fields;
}


class Field {
  public String name;
  public int offset;
  public Object widthBits;
  public Object value;
  public FieldType type;
  public LengthEncoding lengthEncoding;
  public Object defaultValue; // NEW
  public boolean pad;
  public String poly, init, over;
  public String padValue;
}
