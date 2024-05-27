// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp;

import org.aya.lsp.utils.Log;
import org.aya.lsp.utils.LspArgs;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Callable;

public class LspMain extends LspArgs implements Callable<Integer> {
  public static void main(String[] args) {
    new CommandLine(new LspMain()).execute(args);
  }

  @Override public Integer call() throws Exception {
    Log.i("Hello, this is Aya language server");
    var startup = switch (mode) {
      case server -> runServer();
      case client -> runClient();
      case debug -> runDebug();
    };

    throw new UnsupportedOperationException("TODO");
    // LSP.connect(
    //   AyaLanguageClient.class,
    //   client -> new AyaLanguageServer(CompilerAdvisor.inMemory(), client),
    //   startup.in,
    //   startup.out
    // );
    // return 0;
  }

  private static @NotNull Startup runDebug() {
    Log.i("Debug mode, using stdin and stdout");
    return new Startup(System.in, System.out);
  }

  private @NotNull Startup runClient() throws IOException {
    Log.i("Client mode, connecting to %s:%d", host, port);
    var socket = new Socket(host, port);
    return new Startup(new CloseAwareInputStream(socket.getInputStream()), socket.getOutputStream());
  }

  private @NotNull Startup runServer() throws IOException {
    Log.i("Server mode, listening on %s:%d", host, port);
    try (var server = new ServerSocket(port, 0, InetAddress.getByName(host))) {
      var client = server.accept();
      return new Startup(new CloseAwareInputStream(client.getInputStream()), client.getOutputStream());
    }
  }

  private static class CloseAwareInputStream extends InputStream {
    private final InputStream inputStream;

    private CloseAwareInputStream(InputStream inputStream) {
      this.inputStream = inputStream;
    }

    private int closeIfRemoteClosed(int len) throws IOException {
      if (len < 0) inputStream.close();
      return len;
    }

    @Override public int read() throws IOException {
      return closeIfRemoteClosed(inputStream.read());
    }

    @Override public int read(byte @NotNull [] b) throws IOException {
      return closeIfRemoteClosed(inputStream.read(b));
    }

    @Override public int read(byte @NotNull [] b, int off, int len) throws IOException {
      return closeIfRemoteClosed(inputStream.read(b, off, len));
    }
  }

  private record Startup(
    @NotNull InputStream in,
    @NotNull OutputStream out
  ) {
  }
}
