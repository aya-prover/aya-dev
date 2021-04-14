// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import org.aya.lsp.language.AyaLanguageClient;
import org.aya.lsp.server.AyaServer;
import org.aya.prelude.GeneratedVersion;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.function.Function;

public class LspMain {
  public static void main(String[] args) throws IOException {
    var cli = new LspArgs();
    var commander = JCommander.newBuilder().addObject(cli).build();
    try {
      commander.parse(args);
    } catch (ParameterException e) {
      System.err.println(e.getLocalizedMessage());
      System.exit(-1);
    }

    if (cli.version) {
      System.out.println("Aya Language Server v" + GeneratedVersion.VERSION_STRING);
    } else if (cli.help) {
      commander.usage();
      return;
    }

    Log.i("Hello, this is Aya lanauge server");
    var startup = switch (cli.mode) {
      case server -> runServer(cli);
      case client -> runClient(cli);
      case debug -> runDebug(cli);
    };

    var executor = Executors.newSingleThreadExecutor(f -> new Thread(f, "client"));
    var server = new AyaServer();
    var launcher = Launcher.createLauncher(
      server,
      AyaLanguageClient.class,
      startup.in,
      startup.out,
      executor,
      Function.identity()
    );
    server.connect(launcher.getRemoteProxy());
    launcher.startListening();
  }

  private static @NotNull Startup runDebug(@NotNull LspArgs cli) {
    Log.i("Debug mode, using stdin and stdout");
    return new Startup(System.in, System.out);
  }

  private static @NotNull Startup runClient(@NotNull LspArgs cli) throws IOException {
    Log.i("Client mode, connecting to %s:%d", cli.host, cli.port);
    var socket = new Socket(cli.host, cli.port);
    return new Startup(socket.getInputStream(), socket.getOutputStream());
  }

  private static @NotNull Startup runServer(@NotNull LspArgs cli) throws IOException {
    Log.i("Server mode, listening on %s:%d", cli.host, cli.port);
    var server = new ServerSocket(cli.port, 0, InetAddress.getByName(cli.host));
    var client = server.accept();
    server.close();
    return new Startup(client.getInputStream(), client.getOutputStream());
  }

  private static record Startup(
    @NotNull InputStream in,
    @NotNull OutputStream out
  ) {
  }
}
