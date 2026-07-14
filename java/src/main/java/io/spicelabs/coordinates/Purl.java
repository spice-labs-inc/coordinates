// SPDX-License-Identifier: Apache-2.0
/* Copyright 2026 Spice Labs, Inc. & Contributors */

package io.spicelabs.coordinates;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Package URL (purl), per https://github.com/package-url/purl-spec — parse, build, normalize, and
 * validate into a fully-typed value, applying each type's normalization + namespace rules.
 * JDK-only, Java 8.
 */
public final class Purl {

  public static final class PurlException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PurlException(String message) {
      super(message);
    }
  }

  private static final Pattern TYPE = Pattern.compile("^[a-zA-Z][a-zA-Z0-9.+-]*$");
  private static final Pattern QUALIFIER_KEY = Pattern.compile("^[a-zA-Z0-9.\\-_]+$");
  private static final Pattern CHROME_NAME = Pattern.compile("[a-z]{32}");
  private static final Pattern CHROME_VERSION = Pattern.compile("\\d+(\\.\\d+){0,3}");

  // Per-type normalization + validation, derived from the purl-spec type definitions. Only the
  // types
  // that deviate from the default (keep case, optional namespace) are listed.
  private static final Set<String> NS_LOWER =
      setOf(
          "alpm",
          "apk",
          "bitbucket",
          "composer",
          "deb",
          "github",
          "hex",
          "luarocks",
          "qpkg",
          "rpm",
          "vscode-extension",
          "yocto");
  private static final Set<String> NAME_LOWER =
      setOf(
          "alpm",
          "apk",
          "bitbucket",
          "bitnami",
          "chrome-extension",
          "composer",
          "deb",
          "github",
          "hex",
          "luarocks",
          "oci",
          "otp",
          "pub",
          "pypi",
          "vscode-extension");
  private static final Set<String> VER_LOWER =
      setOf("huggingface", "oci", "pypi", "vscode-extension");
  private static final Set<String> NS_REQUIRED =
      setOf(
          "alpm",
          "apk",
          "bitbucket",
          "composer",
          "cpan",
          "deb",
          "github",
          "golang",
          "huggingface",
          "maven",
          "qpkg",
          "rpm",
          "swift",
          "vscode-extension");
  private static final Set<String> NS_PROHIBITED =
      setOf(
          "bazel",
          "bitnami",
          "cargo",
          "chrome-extension",
          "cocoapods",
          "conda",
          "cran",
          "gem",
          "hackage",
          "julia",
          "mlflow",
          "nuget",
          "oci",
          "opam",
          "otp",
          "pub",
          "pypi");

  private static Set<String> setOf(String... values) {
    return new HashSet<String>(Arrays.asList(values));
  }

  public final String type;
  public final String namespace; // nullable
  public final String name;
  public final String version; // nullable
  public final Map<String, String> qualifiers; // never null, possibly empty
  public final String subpath; // nullable

  public Purl(
      String type,
      String namespace,
      String name,
      String version,
      Map<String, String> qualifiers,
      String subpath) {
    this.type = type;
    this.namespace = namespace;
    this.name = name;
    this.version = version;
    this.qualifiers = qualifiers == null ? new LinkedHashMap<String, String>() : qualifiers;
    this.subpath = subpath;
  }

  /** Apply the type's normalization rules, then validate its namespace requirement. */
  public static Purl normalize(Purl p) {
    String namespace = p.namespace;
    String name = p.name;
    String version = p.version;
    if (namespace != null && NS_LOWER.contains(p.type)) {
      namespace = namespace.toLowerCase(Locale.ROOT);
    }
    if (NAME_LOWER.contains(p.type)) {
      name = name.toLowerCase(Locale.ROOT);
    }
    if (p.type.equals("pypi")) {
      name = name.replace('_', '-');
    } else if (p.type.equals("pub")) {
      name = name.replaceAll("[^a-z0-9]", "_");
    }
    if (version != null && VER_LOWER.contains(p.type)) {
      version = version.toLowerCase(Locale.ROOT);
    }

    // Bespoke per-type rules that do not reduce to a flag (from the type definitions).
    if (p.type.equals("mlflow")) {
      String repo = p.qualifiers.get("repository_url");
      if (repo != null && repo.toLowerCase(Locale.ROOT).contains("databricks")) {
        name = name.toLowerCase(Locale.ROOT);
      }
    }
    if (p.type.equals("cpan") && name.contains("::")) {
      throw new PurlException("cpan name must be a distribution name, not a module name (no ::)");
    }
    if (p.type.equals("chrome-extension")) {
      if (!CHROME_NAME.matcher(name).matches()) {
        throw new PurlException("chrome-extension name must be 32 letters (a-z)");
      }
      if (version != null && !CHROME_VERSION.matcher(version).matches()) {
        throw new PurlException("chrome-extension version must be 1-4 dot-separated numbers");
      }
    }
    if (p.type.equals("julia") && p.qualifiers.get("uuid") == null) {
      throw new PurlException("julia purls require a uuid qualifier");
    }

    if (NS_PROHIBITED.contains(p.type) && namespace != null) {
      throw new PurlException("purl type \"" + p.type + "\" does not allow a namespace");
    }
    if (NS_REQUIRED.contains(p.type) && (namespace == null || namespace.isEmpty())) {
      throw new PurlException("purl type \"" + p.type + "\" requires a namespace");
    }
    return new Purl(p.type, namespace, name, version, p.qualifiers, p.subpath);
  }

  public static Purl parse(String input) {
    if (input == null || input.isEmpty()) {
      throw new PurlException("empty purl");
    }
    int colon = input.indexOf(':');
    if (colon < 0 || !input.substring(0, colon).toLowerCase().equals("pkg")) {
      throw new PurlException("a purl must start with the \"pkg:\" scheme");
    }
    String rest = input.substring(colon + 1);
    int start = 0;
    while (start < rest.length() && rest.charAt(start) == '/') {
      start++;
    }
    rest = rest.substring(start);

    String subpath = null;
    int hash = rest.indexOf('#');
    if (hash >= 0) {
      subpath = normalizeSubpath(rest.substring(hash + 1));
      rest = rest.substring(0, hash);
    }

    Map<String, String> qualifiers = new LinkedHashMap<String, String>();
    int question = rest.indexOf('?');
    if (question >= 0) {
      qualifiers = parseQualifiers(rest.substring(question + 1));
      rest = rest.substring(0, question);
    }

    int slash = rest.indexOf('/');
    if (slash < 0) {
      throw new PurlException("a purl must have a type and a name");
    }
    String type = rest.substring(0, slash).toLowerCase(Locale.ROOT);
    if (!TYPE.matcher(type).matches()) {
      throw new PurlException("invalid type: " + type);
    }
    rest = rest.substring(slash + 1);

    String namespace = null;
    String nameVersion = rest;
    int lastSlash = rest.lastIndexOf('/');
    if (lastSlash >= 0) {
      StringBuilder ns = new StringBuilder();
      for (String segment : rest.substring(0, lastSlash).split("/")) {
        if (segment.isEmpty()) {
          continue;
        }
        if (ns.length() > 0) {
          ns.append('/');
        }
        ns.append(decode(segment));
      }
      namespace = ns.length() > 0 ? ns.toString() : null;
      nameVersion = rest.substring(lastSlash + 1);
    }

    // The version's "@" lives in the name segment, so split it only after the namespace is removed
    // —
    // otherwise a namespace like npm's "@babel" would be misread as the version.
    String version = null;
    int at = nameVersion.lastIndexOf('@');
    if (at >= 0) {
      version = decode(nameVersion.substring(at + 1));
      nameVersion = nameVersion.substring(0, at);
    }
    String name = decode(nameVersion);
    if (name.isEmpty()) {
      throw new PurlException("a purl must have a name");
    }

    return normalize(new Purl(type, namespace, name, version, qualifiers, subpath));
  }

  /** The canonical purl string for this value. */
  public String toCanonical() {
    return toString(false);
  }

  /**
   * A Maven-resolvable rendering of this purl: identical to {@link #toCanonical()} except that for
   * {@code pkg:maven} the {@code +} character in the version is left unencoded. All other
   * components and unsafe characters are encoded exactly as in the canonical form.
   */
  public String toMavenUrl() {
    return toString(true);
  }

  private String toString(boolean mavenVersion) {
    String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
    if (t.isEmpty() || !TYPE.matcher(t).matches()) {
      throw new PurlException("invalid type: " + type);
    }
    if (name == null || name.isEmpty()) {
      throw new PurlException("a purl must have a name");
    }
    Purl p = normalize(new Purl(t, namespace, name, version, qualifiers, subpath));

    StringBuilder out = new StringBuilder("pkg:").append(p.type);
    if (p.namespace != null && !p.namespace.isEmpty()) {
      out.append('/');
      boolean first = true;
      for (String segment : p.namespace.split("/")) {
        if (segment.isEmpty()) {
          continue;
        }
        if (!first) {
          out.append('/');
        }
        first = false;
        out.append(encode(segment));
      }
    }
    out.append('/').append(encode(p.name));
    if (p.version != null && !p.version.isEmpty()) {
      out.append('@')
          .append(
              mavenVersion && p.type.equals("maven")
                  ? encodeMavenVersion(p.version)
                  : encode(p.version));
    }

    TreeMap<String, String> sorted = new TreeMap<String, String>();
    for (Map.Entry<String, String> e : p.qualifiers.entrySet()) {
      if (e.getValue() == null || e.getValue().isEmpty()) {
        continue;
      }
      String key = e.getKey().toLowerCase(Locale.ROOT);
      if (!QUALIFIER_KEY.matcher(key).matches()) {
        throw new PurlException("invalid qualifier key: " + e.getKey());
      }
      sorted.put(key, e.getValue());
    }
    if (!sorted.isEmpty()) {
      out.append('?');
      boolean first = true;
      for (Map.Entry<String, String> e : sorted.entrySet()) {
        if (!first) {
          out.append('&');
        }
        first = false;
        out.append(e.getKey()).append('=').append(encode(e.getValue()));
      }
    }

    if (p.subpath != null) {
      String sp = normalizeSubpath(p.subpath);
      if (sp != null) {
        out.append('#');
        boolean first = true;
        for (String segment : sp.split("/")) {
          if (!first) {
            out.append('/');
          }
          first = false;
          out.append(encode(segment));
        }
      }
    }
    return out.toString();
  }

  public static String build(Purl purl) {
    return purl.toCanonical();
  }

  private static Map<String, String> parseQualifiers(String s) {
    Map<String, String> out = new LinkedHashMap<String, String>();
    for (String part : s.split("&")) {
      if (part.isEmpty()) {
        continue;
      }
      int eq = part.indexOf('=');
      String key = (eq < 0 ? part : part.substring(0, eq)).toLowerCase();
      if (!QUALIFIER_KEY.matcher(key).matches()) {
        throw new PurlException("invalid qualifier key: " + key);
      }
      String value = eq < 0 ? "" : decode(part.substring(eq + 1));
      if (value.isEmpty()) {
        continue;
      }
      out.put(key, value);
    }
    return out;
  }

  private static String normalizeSubpath(String s) {
    StringBuilder sb = new StringBuilder();
    for (String raw : s.split("/")) {
      String segment = decode(raw);
      if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append('/');
      }
      sb.append(segment);
    }
    return sb.length() > 0 ? sb.toString() : null;
  }

  private static String decode(String s) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '%') {
        if (i + 2 >= s.length()) {
          throw new PurlException("invalid percent-encoding: " + s);
        }
        int hi = hexValue(s.charAt(i + 1));
        int lo = hexValue(s.charAt(i + 2));
        if (hi < 0 || lo < 0) {
          throw new PurlException("invalid percent-encoding: " + s);
        }
        out.write((hi << 4) | lo);
        i += 2;
      } else {
        byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
        out.write(bytes, 0, bytes.length);
      }
    }
    return new String(out.toByteArray(), StandardCharsets.UTF_8);
  }

  private static int hexValue(char c) {
    if (c >= '0' && c <= '9') {
      return c - '0';
    }
    if (c >= 'a' && c <= 'f') {
      return c - 'a' + 10;
    }
    if (c >= 'A' && c <= 'F') {
      return c - 'A' + 10;
    }
    return -1;
  }

  // Percent-encode a component, leaving the unreserved set and ":" (the canonical purl form).
  private static String encode(String s) {
    StringBuilder sb = new StringBuilder();
    for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
      int u = b & 0xff;
      if ((u >= 'A' && u <= 'Z')
          || (u >= 'a' && u <= 'z')
          || (u >= '0' && u <= '9')
          || u == '-'
          || u == '.'
          || u == '_'
          || u == '~'
          || u == ':') {
        sb.append((char) u);
      } else {
        sb.append('%')
            .append(Character.toUpperCase(Character.forDigit((u >> 4) & 0xF, 16)))
            .append(Character.toUpperCase(Character.forDigit(u & 0xF, 16)));
      }
    }
    return sb.toString();
  }

  /**
   * Percent-encode a Maven version, but leave {@code +} unencoded so the result matches the literal
   * version string used in Maven repositories.
   */
  private static String encodeMavenVersion(String s) {
    StringBuilder out = new StringBuilder();
    for (String part : s.split("\\+", -1)) {
      if (out.length() > 0) {
        out.append('+');
      }
      out.append(encode(part));
    }
    return out.toString();
  }
}
