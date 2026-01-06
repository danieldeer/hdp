package com.hirschdaniel.hdp;

import java.util.Arrays;
import java.util.BitSet;

public class PacketGenerator {
  private final PacketSpecification spec;
  private final byte[] inputData;
  private final BitSet packetBits = new BitSet(16384);
  private int currentBit = 0;

  public PacketGenerator(PacketSpecification spec, byte[] inputData) {
    this.spec = spec;
    this.inputData = inputData != null ? inputData : new byte[0];
  }

  public byte[] generate() {
    embedFields();

    int totalBits;
    if (spec.totalLength != null) {
      totalBits = spec.totalLength * 8;
      if (currentBit < totalBits) {
        padToTotalLength(totalBits, 0xFF);
      }
    } else {
      // variable-length packet
      totalBits = currentBit;
    }

    return bitsToBytes(totalBits);
  }


  private void embedFields() {
    for (Field field : spec.fields) {
      int width = resolveWidth(field);

      switch (field.type) {

        case FIXED -> {
          long val = field.value != null ? parseValue(field.value)
              : (field.defaultValue != null ? parseValue(field.defaultValue) : 0);
          setBits(currentBit, width, val);
          currentBit += width;
        }

        case INPUT -> {
          embedData(field);
        }

        case CRC -> {
          long crcVal = computeCrc(field, currentBit);
          setBits(currentBit, width, crcVal);
          currentBit += width;
        }

        case LENGTH -> {
          long val = encodeLength(field);
          setBits(currentBit, width, val);
          currentBit += width;
        }

        case PAD -> {
          int padValue = (int) parseValue(field.padValue);
          padBits(currentBit, width, padValue != 0);
          currentBit += width;
        }

        default -> {
          long val = field.value != null ? parseValue(field.value) : 0;
          setBits(currentBit, width, val);
          currentBit += width;
        }
      }
    }
  }

  private long encodeLength(Field lengthField) {
    int lenBytes = inputData.length;
    int lenBits = lenBytes * 8;

    return switch (lengthField.lengthEncoding) {
      case LengthEncoding.BYTES -> lenBytes;
      case LengthEncoding.BYTESMINUSONE -> lenBytes - 1;
      case LengthEncoding.BITS -> lenBits;
      case LengthEncoding.BITSMINUSONE -> lenBits - 1;

      default -> throw new IllegalArgumentException(
          String.format("Unknown compute mode: %s. Accepted values are ",
              Arrays.asList(LengthEncoding.values())));
    };
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
    if (FieldType.PAD.equals(f.type)) {
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
    if (f.widthBits instanceof String widthString) {
      if (widthString.equalsIgnoreCase("dynamic"))
        return inputData.length * 8;
      if (widthString.startsWith("0b"))
        return Integer.parseInt(widthString.substring(2), 2);
      if (widthString.startsWith("0x"))
        return Integer.parseInt(widthString.substring(2), 16);
      try {
        return Integer.parseInt(widthString);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid widthBits: " + widthString);
      }
    }
    throw new IllegalArgumentException("Unsupported widthBits type: " + f.widthBits);
  }


  // helper method that ignores pad fields and avoids recursion
  private int resolveWidthSafe(Field f) {
    if (f.type.equals(FieldType.PAD)) {
      return 0; // don't count pad fields when computing pad width
    }
    if (f.type.equals(FieldType.INPUT)) {
      return inputData.length * 8;
    }
    if (f.widthBits instanceof Number number) {
      return number.intValue();
    }
    if (f.widthBits instanceof String widthString) {
      return Integer.parseInt(widthString);
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
