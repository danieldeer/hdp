package com.hirschdaniel.hdp;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PacketSpec {
  public String name;
  public int totalLength;
  public List<Field> fields;

  public String getName() {
    return name;
  }

  public int getTotalLength() {
    return totalLength;
  }

  public List<Field> getFields() {
    return fields;
  }
}


class Field {
  public String name;
  public int offset;
  public Object widthBits;
  public Object value;
  public String type;
  public String compute;
  public boolean pad;
  public String poly, init, over;
  public String padValue;

  public String getName() {
    return name;
  }

  public int getOffset() {
    return offset;
  }

  public Object getWidthBits() {
    return widthBits;
  }

  public Object getValue() {
    return value;
  }

  public String getType() {
    return type;
  }

  public String getCompute() {
    return compute;
  }

  public boolean isPad() {
    return pad;
  }

  public String getPoly() {
    return poly;
  }

  public String getInit() {
    return init;
  }

  public String getOver() {
    return over;
  }

  public String getPadValue() {
    return padValue;
  }
}
