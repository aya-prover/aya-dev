package org.mzi.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.math.BigInteger;

/**
 * @author ice1000
 */
public record Version(
  @NotNull BigInteger major,
  @NotNull BigInteger minor,
  @NotNull BigInteger patch
) implements Comparable<Version>, Serializable {
  @Contract(value = "_ -> new", pure = true)
  public static @NotNull Version create(@NotNull String version) {
    var split = version.trim().split("\\.");
    return switch (split.length) {
      case 0 -> throw new IllegalArgumentException("Invalid version: " + version);
      case 1 -> new Version(new BigInteger(split[0]), BigInteger.ZERO, BigInteger.ZERO);
      case 2 -> new Version(new BigInteger(split[0]), new BigInteger(split[1]), BigInteger.ZERO);
      default -> new Version(new BigInteger(split[0]), new BigInteger(split[1]), new BigInteger(split[2]));
    };
  }

  public Version(String major, String minor, String patch) {
    this(new BigInteger(major), new BigInteger(minor), new BigInteger(patch));
  }

  public Version(long major, long minor, long patch) {
    this(BigInteger.valueOf(major), BigInteger.valueOf(minor), BigInteger.valueOf(patch));
  }

  @Contract(pure = true)
  public @NotNull String getLongString() {
    return major + "." + minor + "." + patch;
  }

  @Override
  public String toString() {
    return BigInteger.ZERO.equals(patch)
      ? BigInteger.ZERO.equals(minor)
      ? major.toString()
      : major + "." + minor
      : major + "." + minor + "." + patch;
  }

  @Override
  public int compareTo(Version o) {
    int i = major.compareTo(o.major);
    if (i != 0) return i;
    int j = minor.compareTo(o.minor);
    return j != 0 ? j : patch.compareTo(o.patch);
  }
}
