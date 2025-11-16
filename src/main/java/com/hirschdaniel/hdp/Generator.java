package com.hirschdaniel.hdp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.Callable;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "hdp", version = "1.0.0",
    description = "A rock-solid CLI utility to generate packets.", mixinStandardHelpOptions = true,
    subcommands = {CommandLine.HelpCommand.class})
public class Generator implements Callable<Integer> {

  @Parameters(arity = "1", description = "Input data file (binary/hex)")
  File inputFile;

  @Option(names = "--spec", required = true, description = "Packet spec JSON")
  File specFile;

  @Option(names = "--output", required = true, description = "Output binary file")
  File outputFile;

  @Override
  public Integer call() {
    if (!inputFile.exists()) {
      System.err.printf("Input file %s doesn't exist\n", inputFile.getAbsoluteFile());
      return -1;
    }

    try {

      byte[] input = loadInput(inputFile);
      PacketSpec spec = loadSpec(specFile);

      PacketGenerator gen = new PacketGenerator(spec, input);
      byte[] packet = gen.generate();

      Files.write(outputFile.toPath(), packet);
      System.out.printf("Generated %d bytes output: %s\n", packet.length,
          outputFile.getAbsolutePath());
      return 0;
    } catch (Exception e) {
      System.err.println(e);
      return 1;
    }
  }

  private PacketSpec loadSpec(File specFile) {
    try {
      ObjectMapper mapper =
          JsonMapper.builder().configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true).build();
      return mapper.readValue(specFile, PacketSpec.class);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }
    return null;
  }

  private byte[] loadInput(File f) throws IOException {
    return Files.readAllBytes(f.toPath());
  }

  public static void main(String[] args) {
    int exitCode =
        new CommandLine(new Generator()).setCaseInsensitiveEnumValuesAllowed(true).execute(args);
    System.exit(exitCode);
  }
}
