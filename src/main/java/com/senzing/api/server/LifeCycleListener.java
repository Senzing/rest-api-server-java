package com.senzing.api.server;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.component.LifeCycle;

import java.net.InetAddress;

class LifeCycleListener implements LifeCycle.Listener {
  private String serverDescription;
  private int httpPort;
  private InetAddress ipAddr;
  private String basePath;
  private Server jettyServer;
  private FileMonitor fileMonitor;

  public LifeCycleListener(String       serverDesription,
                           Server       jettyServer,
                           int          httpPort,
                           String       basePath,
                           InetAddress  ipAddress,
                           FileMonitor  fileMonitor)
  {
    this.serverDescription  = serverDesription;
    this.httpPort           = httpPort;
    this.ipAddr             = ipAddress;
    this.basePath           = basePath;
    this.jettyServer        = jettyServer;
    this.fileMonitor        = fileMonitor;
  }

  public void lifeCycleStarting(LifeCycle event) {
    System.out.println();
    if (this.httpPort != 0) {
      System.out.println("Starting " + this.serverDescription
                             + " on port " + this.httpPort + "....");
    } else {
      System.out.println(
          "Starting " + this.serverDescription + " with rotating port....");

    }
    System.out.println();
  }

  public void lifeCycleStarted(LifeCycle event) {
    int port = this.httpPort;
    if (port == 0) {
      port = ((ServerConnector)(jettyServer.getConnectors()[0])).getLocalPort();
    }
    System.out.println("Started " + this.serverDescription
                           + " on port " + port + ".");
    System.out.println();
    System.out.println("Server running at:");
    System.out.println("http://" + this.ipAddr.getHostAddress()
                       + ":" + port + this.basePath);
    System.out.println();
    if (this.fileMonitor != null) {
      this.fileMonitor.signalReady();
    }
  }

  public void lifeCycleFailure(LifeCycle event, Throwable cause) {
    if (this.httpPort != 0) {
      System.err.println("Failed to start " + this.serverDescription
                             + " on port " + this.httpPort + ".");
    } else {
      System.err.println(
          "Failed to start " + this.serverDescription + " with rotating port.");
    }
    System.err.println();
    System.err.println(cause);
  }

  public void lifeCycleStopping(LifeCycle event) {
    System.out.println("Stopping " + this.serverDescription + "....");
    System.out.println();
  }

  public void lifeCycleStopped(LifeCycle event) {
    System.out.println("Stopped " + this.serverDescription + ".");
    System.out.println();
  }
}
