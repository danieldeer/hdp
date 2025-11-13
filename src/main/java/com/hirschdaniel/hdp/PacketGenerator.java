package com.hirschdaniel.hdp;

import java.util.BitSet;

public class PacketGenerator {
  private final PacketSpec spec;
  private final byte[] inputData;
  private final BitSet packetBits = new BitSet(16384);
  private int currentBit = 0;
  private int counter = 0;

  public PacketGenerator(PacketSpec spec, byte[] inputData) {
    this.spec = spec;
    this.inputData = inputData != null ? inputData : new byte[0];
  }

  public byte[] generate() {
    int totalBits = spec.totalLength * 8;
    embedFields();
    if (currentBit < totalBits) {
      padToTotalLength(totalBits, 0xFF);
    }
    return bitsToBytes(totalBits);
  }

  private void embedFields() {
    for (Field field : spec.fields) {
      String type = field.type != null ? field.type : "fixed";
      int width = resolveWidth(field);

      switch (type) {
        case "fixed" -> {
          long val = parseValue(field.value);
          setBits(currentBit, width, val);
          currentBit += width;
        }
        case "counter" -> {
          long val = counter++;
          setBits(currentBit, width, val);
          currentBit += width;
        }
        case "input" -> {
          embedData(field);
        }
        case "crc" -> {
          long crcVal = computeCrc(field, currentBit);
          setBits(currentBit, width, crcVal);
          currentBit += width;
        }
        case "length" -> {
          // depends how length is encoded. Usually it's just length in bytes
          // but can also be length in bits
          // or length in bytes -1. For now, this supports only length in bytes
          long len = inputData.length;
          setBits(currentBit, width, len);
          currentBit += width;
        }
        case "pad" -> {
          int padValue = (int) parseValue(field.padValue);
          padBits(currentBit, width, true);
          currentBit += width;
        }
        default -> {
          long val = parseValue(field.value);
          setBits(currentBit, width, val);
          currentBit += width;
        }
      }
    }

  }

  private void padBits(int startBit, int width, boolean padValue) {
    packetBits.set(startBit, startBit + width, padValue);
  }

  private void embedData(Field f) {
    for (byte b : inputData) {
      for (int bit = 7; bit >= 0; bit--) {
        packetBits.set(currentBit++, (b & (1 << bit)) != 0);
      }
    }

    if (f.pad) {
      while (currentBit % 8 != 0) {
        packetBits.clear(currentBit++);
      }
    }
  }

  private void padToTotalLength(int totalBits, int padValue) {
    // padValue = 0xFF → all bits = 1
    boolean bit = (padValue & 0x80) != 0; // highest bit used as pattern reference (all 1s)
    while (currentBit < totalBits) {
      packetBits.set(currentBit++, bit);
    }
  }

  private int resolveWidth(Field f) {
    if ("pad".equalsIgnoreCase(f.type)) {
      // Compute total bits already consumed by all *other* fields
      int usedBits = spec.fields.stream().filter(x -> x != f) // exclude the pad field itself
          .mapToInt(this::resolveWidthSafe).sum();

      int padBits = spec.totalLength * 8 - usedBits;
      if (padBits < 0) {
        throw new IllegalArgumentException(
            "Total field lengths exceed totalLength in spec: " + spec.name);
      }
      return padBits;
    }

    if (f.widthBits instanceof Number n) {
      return n.intValue();
    }
    if (f.widthBits instanceof String s) {
      if (s.equalsIgnoreCase("dynamic"))
        return inputData.length * 8;
      if (s.startsWith("0b"))
        return Integer.parseInt(s.substring(2), 2);
      if (s.startsWith("0x"))
        return Integer.parseInt(s.substring(2), 16);
      try {
        return Integer.parseInt(s);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid widthBits: " + s);
      }
    }
    throw new IllegalArgumentException("Unsupported widthBits type: " + f.widthBits);
  }


  // helper method that ignores pad fields and avoids recursion
  private int resolveWidthSafe(Field f) {
    if ("pad".equalsIgnoreCase(f.type)) {
      return 0; // don't count pad fields when computing pad width
    }
    if ("input".equalsIgnoreCase(f.type)) {
      return inputData.length * 8;
    }
    if (f.widthBits instanceof Number n) {
      return n.intValue();
    }
    if (f.widthBits instanceof String s) {
      return Integer.parseInt(s);
    }
    throw new RuntimeException(
        "What's wrong with the width of field: " + f.name + ", widthBits=" + f.widthBits);
  }

  private long parseValue(Object val) {
    if (val == null)
      return 0;
    if (val instanceof Number n)
      return n.longValue();
    if (val instanceof String s) {
      if (s.startsWith("0b"))
        return Long.parseLong(s.substring(2), 2);
      if (s.startsWith("0x"))
        return Long.parseLong(s.substring(2), 16);
      return Long.parseLong(s);
    }
    throw new IllegalArgumentException("Unsupported value: " + val);
  }

  private void setBits(int start, int width, long value) {
    for (int i = 0; i < width; i++) {
      boolean bit = ((value >> (width - 1 - i)) & 1) == 1;
      packetBits.set(start + i, bit);
    }
  }

  private long computeCrc(Field f, int crcStartBit) {
    int poly = Integer.parseInt(f.poly.substring(2), 16);
    int crc = Integer.parseInt(f.init.substring(2), 16);

    int totalBits = crcStartBit;
    for (int i = 0; i < totalBits; i++) {
      boolean bit = packetBits.get(i);
      boolean msb = ((crc >> 15) & 1) == 1;
      crc = ((crc << 1) & 0xFFFF) | (bit ? 1 : 0);
      if (msb)
        crc ^= poly;
    }

    return crc & 0xFFFF;
  }

  private byte[] bitsToBytes(int totalBits) {
    byte[] bytes = new byte[(totalBits + 7) / 8];
    for (int i = 0; i < totalBits; i++) {
      if (packetBits.get(i)) {
        bytes[i / 8] |= 1 << (7 - (i % 8));
      }
    }
    return bytes;
  }
}
