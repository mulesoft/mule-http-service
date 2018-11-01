/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server;

import static org.mule.runtime.api.util.Preconditions.checkArgument;
import static org.mule.service.http.impl.service.server.grizzly.DefaultMethodRequestMatcher.getMethodsListRepresentation;
import static org.mule.service.http.impl.service.server.grizzly.HttpParser.decodePath;
import static org.mule.service.http.impl.service.server.grizzly.HttpParser.normalizePathWithSpacesOrEncodedSpaces;
import static org.slf4j.LoggerFactory.getLogger;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.core.api.config.i18n.CoreMessages;
import org.mule.runtime.core.api.util.StringUtils;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.PathAndMethodRequestMatcher;
import org.mule.runtime.http.api.server.RequestHandler;
import org.mule.runtime.http.api.server.RequestHandlerManager;
import org.mule.runtime.http.api.server.ServerAddress;
import org.mule.service.http.impl.service.server.grizzly.AcceptsAllMethodsRequestMatcher;

import com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.slf4j.Logger;

/**
 * Registry of servers and its handlers, which allows searching for handlers and introducing new ones (while allowing them to be
 * managed).
 */
public class HttpListenerRegistry implements RequestHandlerProvider {

  private static final String WILDCARD_CHARACTER = "*";
  private static final String SLASH = "/";
  private static final Logger LOGGER = getLogger(HttpListenerRegistry.class);

  private final ServerAddressMap<HttpServer> serverAddressToServerMap = new ServerAddressMap<>();
  private final Map<HttpServer, ServerAddressRequestHandlerRegistry> requestHandlerPerServerAddress = new HashMap<>();

  /**
   * Introduces a new {@link RequestHandler} for requests matching a given {@link PathAndMethodRequestMatcher} in the provided
   * {@link HttpServer}.
   *
   * @param server where the handler should be added
   * @param requestHandler the handler to add
   * @param requestMatcher the matcher to be applied for the handler
   * @return a {@link RequestHandlerManager} for the added handler that allows enabling, disabling and disposing it
   */
  public synchronized RequestHandlerManager addRequestHandler(final HttpServer server, final RequestHandler requestHandler,
                                                              final PathAndMethodRequestMatcher requestMatcher) {
    ServerAddressRequestHandlerRegistry serverAddressRequestHandlerRegistry = this.requestHandlerPerServerAddress.get(server);
    if (serverAddressRequestHandlerRegistry == null) {
      serverAddressRequestHandlerRegistry = new ServerAddressRequestHandlerRegistry();
      requestHandlerPerServerAddress.put(server, serverAddressRequestHandlerRegistry);
      serverAddressToServerMap.put(server.getServerAddress(), server);
    }
    return serverAddressRequestHandlerRegistry.addRequestHandler(requestMatcher, requestHandler);
  }

  /**
   * Removes all handlers for a given {@link HttpServer}.
   *
   * @param server whose handlers will be removed
   */
  public synchronized void removeHandlersFor(HttpServer server) {
    requestHandlerPerServerAddress.remove(server);
    serverAddressToServerMap.remove(server.getServerAddress());
  }

  @Override
  public synchronized boolean hasHandlerFor(ServerAddress serverAddress) {
    return serverAddressToServerMap.get(serverAddress) != null;
  }

  @Override
  public RequestHandler getRequestHandler(ServerAddress serverAddress, final HttpRequest request) {
    LOGGER.debug("Looking RequestHandler for request: {}", request.getPath());
    final HttpServer server = serverAddressToServerMap.get(serverAddress);
    if (server != null && !server.isStopping() && !server.isStopped()) {
      final ServerAddressRequestHandlerRegistry serverAddressRequestHandlerRegistry = requestHandlerPerServerAddress.get(server);
      if (serverAddressRequestHandlerRegistry != null) {
        return serverAddressRequestHandlerRegistry.findRequestHandler(request);
      }
    }
    LOGGER.debug("No RequestHandler found for request: {}", request.getPath());
    return NoListenerRequestHandler.getInstance();
  }

  /**
   * Registry of handlers for a server, which maintains {@link Path} structures for root (/), catch all (/*) and server (*)
   * requests.
   */
  public class ServerAddressRequestHandlerRegistry {

    private Path serverRequestHandler;
    private Path rootPath = new Path("root", null);
    private Path catchAllPath = new Path(WILDCARD_CHARACTER, null);
    private Set<String> paths = new HashSet<>();

    /**
     * Adds a handler to the specified path, analyzing the current {@link Path} structure to create new paths if necessary.
     *
     * @param requestMatcher the matcher of paths and methods to use
     * @param requestHandler the handler for requests to add
     * @return a {@link RequestHandlerManager} for the added handler that allows enabling, disabling and disposing it
     */
    public synchronized RequestHandlerManager addRequestHandler(final PathAndMethodRequestMatcher requestMatcher,
                                                                final RequestHandler requestHandler) {
      String requestMatcherPath = normalizePathWithSpacesOrEncodedSpaces(requestMatcher.getPath());
      checkArgument(requestMatcherPath.startsWith(SLASH) || requestMatcherPath.equals(WILDCARD_CHARACTER),
                    "path parameter must start with /");
      validateCollision(requestMatcher);
      List<String> matcherMethods = requestMatcher.getMethodRequestMatcher().getMethods();
      paths.add(getMethodAndPath(getMethodsListRepresentation(matcherMethods), requestMatcherPath));
      Path currentPath = rootPath;
      final RequestHandlerMatcherPair addedRequestHandlerMatcherPair;
      final Path requestHandlerOwner;
      if (requestMatcherPath.equals(WILDCARD_CHARACTER)) {
        serverRequestHandler = new Path("server", null);
        addedRequestHandlerMatcherPair = new RequestHandlerMatcherPair(requestMatcher, requestHandler);
        requestHandlerOwner = serverRequestHandler;
        serverRequestHandler.addRequestHandlerMatcherPair(addedRequestHandlerMatcherPair);
      } else if (requestMatcherPath.equals("/*")) {
        addedRequestHandlerMatcherPair = new RequestHandlerMatcherPair(requestMatcher, requestHandler);
        requestHandlerOwner = catchAllPath;
        catchAllPath.addRequestHandlerMatcherPair(addedRequestHandlerMatcherPair);
      } else if (requestMatcherPath.equals(SLASH)) {
        addedRequestHandlerMatcherPair = new RequestHandlerMatcherPair(requestMatcher, requestHandler);
        requestHandlerOwner = rootPath;
        rootPath.addRequestHandlerMatcherPair(addedRequestHandlerMatcherPair);
      } else {
        final String[] pathParts = splitPath(requestMatcherPath);
        int insertionLevel = getPathPartsSize(requestMatcherPath);
        for (int i = 1; i < insertionLevel - 1; i++) {
          String currentPathName = pathParts[i];
          Path path = currentPath.getChildPath(currentPathName, null);
          if (i != insertionLevel - 1) {
            if (path == null) {
              path = new Path(currentPathName, path);
              currentPath.addChildPath(currentPathName, path);
            }
          }

          currentPath = path;
        }
        String currentPathName = pathParts[insertionLevel - 1];
        Path path = currentPath.getLastChildPath(currentPathName);
        if (path == null) {
          path = new Path(currentPathName, path);
          currentPath.addChildPath(currentPathName, path);
        }
        if (requestMatcherPath.endsWith(WILDCARD_CHARACTER)) {
          addedRequestHandlerMatcherPair = new RequestHandlerMatcherPair(requestMatcher, requestHandler);
          path.addWildcardRequestHandler(addedRequestHandlerMatcherPair);
          requestHandlerOwner = path;
        } else {
          addedRequestHandlerMatcherPair = new RequestHandlerMatcherPair(requestMatcher, requestHandler);
          path.addRequestHandlerMatcherPair(addedRequestHandlerMatcherPair);
          requestHandlerOwner = path;
        }
      }
      return new DefaultRequestHandlerManager(requestHandlerOwner, addedRequestHandlerMatcherPair, this);
    }

    private void validateCollision(PathAndMethodRequestMatcher newListenerRequestMatcher) {
      final String newListenerRequestMatcherPath = newListenerRequestMatcher.getPath();
      final Stack<Path> possibleRequestHandlers = findPossibleRequestHandlers(newListenerRequestMatcherPath);
      for (Path possibleRequestHandler : possibleRequestHandlers) {
        final List<RequestHandlerMatcherPair> requestHandlerMatcherPairs = possibleRequestHandler.getRequestHandlerMatcherPairs();
        for (RequestHandlerMatcherPair requestHandlerMatcherPair : requestHandlerMatcherPairs) {
          final PathAndMethodRequestMatcher requestMatcher = requestHandlerMatcherPair.getRequestMatcher();
          final String possibleCollisionRequestMatcherPath = requestMatcher.getPath();
          if (isSameDepth(possibleCollisionRequestMatcherPath, newListenerRequestMatcherPath)) {
            if (newListenerRequestMatcher.getMethodRequestMatcher().intersectsWith(requestMatcher.getMethodRequestMatcher())) {
              String possibleCollisionLastPathPart = getLastPathPortion(possibleCollisionRequestMatcherPath);
              String newListenerRequestMatcherLastPathPart = getLastPathPortion(newListenerRequestMatcherPath);
              if (possibleCollisionLastPathPart.equals(newListenerRequestMatcherLastPathPart)
                  || (isCatchAllPath(possibleCollisionLastPathPart) && isCatchAllPath(newListenerRequestMatcherLastPathPart))
                  || (isCatchAllPath(possibleCollisionLastPathPart) && isUriParameter(newListenerRequestMatcherLastPathPart))
                  || (isUriParameter(possibleCollisionLastPathPart) && isCatchAllPath(newListenerRequestMatcherLastPathPart)
                      || (isUriParameter(possibleCollisionLastPathPart)
                          && isUriParameter(newListenerRequestMatcherLastPathPart)))) {
                throw new MuleRuntimeException(CoreMessages.createStaticMessage(String
                    .format("Already exists a listener matching that path and methods. Listener matching %s new listener %s",
                            requestMatcher, newListenerRequestMatcher)));
              }
            }
          }
        }
      }
    }

    /**
     * Navigates the current {@link Path} structure searching for possible handlers for the request path required, then checks
     * which one matches the request method as well. Handles availability, existence and permission as well.
     *
     * @param request the received {@link HttpRequest}
     * @return the corresponding {@link RequestHandler}
     */
    public RequestHandler findRequestHandler(final HttpRequest request) {
      final String fullPathName;
      try {
        fullPathName = decodePath(request.getPath());
      } catch (DecodingException e) {
        return BadRequestHandler.getInstance();
      }
      checkArgument(fullPathName.startsWith(SLASH), "path parameter must start with /");
      Stack<Path> foundPaths = findPossibleRequestHandlers(fullPathName);
      boolean methodNotAllowed = false;
      RequestHandlerMatcherPair requestHandlerMatcherPair = null;
      while (!foundPaths.empty()) {
        final Path path = foundPaths.pop();
        List<RequestHandlerMatcherPair> requestHandlerMatcherPairs = path.getRequestHandlerMatcherPairs();

        if (requestHandlerMatcherPairs == null && path.getCatchAll() != null) {
          requestHandlerMatcherPairs = path.getCatchAll().requestHandlerMatcherPairs;
        }
        requestHandlerMatcherPair = findRequestHandlerMatcherPair(requestHandlerMatcherPairs, request);

        if (requestHandlerMatcherPair != null) {
          break;
        }
        if (!requestHandlerMatcherPairs.isEmpty()) {
          // there were matching paths but no matching methods
          methodNotAllowed = true;
        }
      }
      if (requestHandlerMatcherPair == null) {
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info("No listener found for request: " + getMethodAndPath(request.getMethod(), request.getPath()));
          LOGGER.info("Available listeners are: [{}]", Joiner.on(", ").join(this.paths));
        }
        if (methodNotAllowed) {
          return NoMethodRequestHandler.getInstance();
        }
        return NoListenerRequestHandler.getInstance();
      }
      if (!requestHandlerMatcherPair.isRunning()) {
        return ServiceTemporarilyUnavailableListenerRequestHandler.getInstance();
      }
      return requestHandlerMatcherPair.getRequestHandler();
    }

    /**
     * Removes the request matcher path from the available ones. This method will not affect the current {@link Path} structure,
     * which will require explicit removal of the associated request handler via {@link Path#removeRequestHandlerMatcherPair(RequestHandlerMatcherPair)}
     * so that the tree-like structure prunes itself if necessary.
     *
     * @param requestMatcher the matcher for requests
     */
    public void removeRequestHandler(PathAndMethodRequestMatcher requestMatcher) {
      paths.remove(getMethodAndPath(requestMatcher));
    }

    private String getMethodAndPath(PathAndMethodRequestMatcher matcher) {
      return getMethodAndPath(getMethodsListRepresentation(matcher.getMethodRequestMatcher().getMethods()), matcher.getPath());
    }

    private String getMethodAndPath(String method, String path) {
      return "(" + method + ")" + path;
    }

    private Stack<Path> findPossibleRequestHandlers(String fullPathName) {
      Path currentPath = rootPath;
      Path auxPath = null;
      final String[] pathParts = splitPath(fullPathName);
      Stack<Path> foundPaths = new Stack<>();
      foundPaths.add(catchAllPath);
      if (fullPathName.equals(WILDCARD_CHARACTER)) {
        foundPaths.push(serverRequestHandler);
        return foundPaths;
      }
      if (fullPathName.equals(SLASH)) {
        foundPaths.push(rootPath);
        return foundPaths;
      }
      for (int i = 1; i < pathParts.length && currentPath != null; i++) {
        String currentPathName = pathParts[i];
        Path path = currentPath.getChildPath(currentPathName, i < pathParts.length - 1 ? pathParts[i + 1] : null);

        if (path == null) {
          addCatchAllPathIfNotNull(currentPath, foundPaths);
          path = currentPath.getCatchAllUriParam();
        } else if (path.getCatchAll() != null) {
          auxPath = path;
        }
        if (i == pathParts.length - 1 || path == null) {
          if (auxPath != null) {
            addCatchAllPathIfNotNull(auxPath, foundPaths);
          }
          if (path != null) {
            addCatchAllPathIfNotNull(path, foundPaths);
            foundPaths.push(path);
          } else {
            addCatchAllPathIfNotNull(currentPath, foundPaths);
          }
        }
        currentPath = path;
      }
      return foundPaths;
    }

    private void addCatchAllPathIfNotNull(Path currentPath, Stack<Path> foundPaths) {
      final Path catchAllPath = currentPath.getCatchAll();
      if (catchAllPath != null) {
        foundPaths.push(catchAllPath);
      }
    }

    private RequestHandlerMatcherPair findRequestHandlerMatcherPair(List<RequestHandlerMatcherPair> requestHandlerMatcherPairs,
                                                                    HttpRequest request) {
      for (RequestHandlerMatcherPair requestHandlerMatcherPair : requestHandlerMatcherPairs) {
        if (requestHandlerMatcherPair.getRequestMatcher().matches(request)) {
          return requestHandlerMatcherPair;
        }
      }
      return null;
    }
  }

  private boolean isUriParameter(String pathPart) {
    return (pathPart.startsWith("{") || pathPart.startsWith("/{")) && pathPart.endsWith("}");
  }

  private String getLastPathPortion(String possibleCollisionRequestMatcherPath) {
    final String[] parts = splitPath(possibleCollisionRequestMatcherPath);
    if (parts.length == 0) {
      return StringUtils.EMPTY;
    }
    return parts[parts.length - 1];
  }

  private boolean isSameDepth(String possibleCollisionRequestMatcherPath, String newListenerRequestMatcherPath) {
    return getPathPartsSize(possibleCollisionRequestMatcherPath) == getPathPartsSize(newListenerRequestMatcherPath);
  }

  private int getPathPartsSize(String path) {
    int pathSize = splitPath(path).length - 1;
    pathSize += (path.endsWith(SLASH) ? 1 : 0);
    return pathSize;
  }

  private String[] splitPath(String path) {
    if (path.endsWith(SLASH)) {
      // Remove the last slash
      path = path.substring(0, path.length() - 1);
    }
    return path.split(SLASH, -1);
  }

  private boolean isCatchAllPath(String path) {
    return WILDCARD_CHARACTER.equals(path);
  }

  /**
   * Represents a URI path, which can be a parent to regular sub paths, a catch all sub path (/*) and a URI param path (/{param}).
   * Request handler and matcher pairs (handlers for different methods, for example) can be added and removed from it. If all
   * its handlers and children's handlers' are removed, it will notify its parent the path itself can be removed. This means that
   * the tree-like structure that results from binding paths together changes as handlers are created and disposed so special care
   * needs to be taken regarding available paths.
   */
  public class Path {

    List<RequestHandlerMatcherPair> requestHandlerMatcherPairs = new ArrayList<>();

    private String name;
    private Path parent;
    private Map<String, Path> subPaths = new HashMap<>();
    private Path catchAll;
    private Path catchAllUriParam;

    /**
     * Creates a new instance for the given name and with the provided parent.
     *
     * @param name for this path
     * @param parent of this path
     */
    public Path(String name, Path parent) {
      this.name = name;
      this.parent = parent;
    }

    /**
     * @return the catch all path for this one (/*)
     */
    public Path getCatchAll() {
      return catchAll;
    }

    /**
     * @return the catch all uri param path for this one (/{param})
     */
    public Path getCatchAllUriParam() {
      return catchAllUriParam;
    }

    /**
     * @param subPath the sub part of the path to find
     * @param nextSubPath the next sub part of the path. It is useful for deciding between static paths and middle-wildcard paths when they are similar.
     * @return the node with the existent mappings. null if there's no such node.
     */
    public Path getChildPath(final String subPath, String nextSubPath) {
      if (isCatchAllPath(subPath) || isUriParameter(subPath)
          || (isViablePath(nextSubPath) && !matchesNextSubPaths(subPath, nextSubPath))) {
        return getCatchAllUriParam();
      }
      Path path = subPaths.get(subPath);
      return path;
    }

    /**
     * @param nextSubPath the path following the would be uri param
     * @return whether there's an uri param where the following path is available
     */
    private boolean isViablePath(String nextSubPath) {
      if (getCatchAllUriParam() != null && nextSubPath != null) {
        return getCatchAllUriParam().subPaths.containsKey(nextSubPath);
      }
      return false;
    }

    /**
     * @param subPath the sub path to check
     * @param nextSubPath the path following the sub path
     * @return whether there's a sub path where the following path is available
     */
    private boolean matchesNextSubPaths(String subPath, String nextSubPath) {
      if (subPaths.containsKey(subPath)) {
        Path nextPath = subPaths.get(subPath);
        if (nextPath.getSubPaths() != null) {
          return nextPath.getSubPaths().containsKey(nextSubPath);
        }
      }
      return false;
    }

    /**
     * @param subPath a sub part of the path
     * @return the node with the existent mappings. null if there's no such node.
     */
    public Path getLastChildPath(final String subPath) {
      if (isCatchAllPath(subPath) || isUriParameter(subPath)) {
        return getCatchAllUriParam();
      }
      Path path = subPaths.get(subPath);
      return path;
    }

    /**
     * Adds a new request handler and matcher for this path.
     *
     * @param requestHandlerMatcherPair the pair to add
     */
    public void addRequestHandlerMatcherPair(final RequestHandlerMatcherPair requestHandlerMatcherPair) {
      if (requestHandlerMatcherPair.getRequestMatcher().getMethodRequestMatcher() instanceof AcceptsAllMethodsRequestMatcher) {
        this.requestHandlerMatcherPairs.add(requestHandlerMatcherPair);
      } else {
        this.requestHandlerMatcherPairs.add(0, requestHandlerMatcherPair);
      }
    }

    /**
     * Adds a new sub or uri param path.
     *
     * @param pathName the path name
     * @param path the representation of this path
     */
    public void addChildPath(final String pathName, final Path path) {
      if (pathName.equals(WILDCARD_CHARACTER) || pathName.endsWith("}")) {
        catchAllUriParam = path;
      } else {
        subPaths.put(pathName, path);
      }
    }

    /**
     * Removes a sub path and checks whether this path itself should be removed.
     *
     * @param pathName the path name
     */
    public void removeChildPath(final String pathName) {
      subPaths.remove(pathName);
      removeSelfIfEmpty();
    }

    /**
     * @return whether this path is empty, meaning it has no handlers or sub paths of any kind which are in turn non empty.
     */
    public boolean isEmpty() {
      return requestHandlerMatcherPairs.isEmpty() && subPaths.isEmpty() &&
          (catchAll == null || catchAll.isEmpty()) &&
          (catchAllUriParam == null || catchAllUriParam.isEmpty());
    }

    /**
     * @return the map of sub paths available
     */
    public Map<String, Path> getSubPaths() {
      return subPaths;
    }

    /**
     * @return the list of handlers for this path
     */
    public List<RequestHandlerMatcherPair> getRequestHandlerMatcherPairs() {
      return requestHandlerMatcherPairs;
    }

    /**
     * Adds a request handler matcher pair for the catch all sub path (/*).
     *
     * @param requestHandlerMatcherPair the pair to add
     */
    public void addWildcardRequestHandler(RequestHandlerMatcherPair requestHandlerMatcherPair) {
      if (this.catchAll == null) {
        this.catchAll = new Path(WILDCARD_CHARACTER, this);
      }
      this.catchAll.addRequestHandlerMatcherPair(requestHandlerMatcherPair);
    }

    /**
     * Removes a request handler and matcher pair whether it's in a regular sub path, the catch all sub path or a uri param
     * sub path. The latter two will be removed if empty as will the path it self if now empty.
     *
     * @param requestHandlerMatcherPair
     * @return whether the removal was successful
     */
    public boolean removeRequestHandlerMatcherPair(RequestHandlerMatcherPair requestHandlerMatcherPair) {
      if (this.requestHandlerMatcherPairs.remove(requestHandlerMatcherPair)) {
        removeSelfIfEmpty();
        return true;
      }
      if (this.catchAll != null && this.catchAll.removeRequestHandlerMatcherPair(requestHandlerMatcherPair)) {
        if (this.catchAll.isEmpty()) {
          this.catchAll = null;
        }
        removeSelfIfEmpty();
        return true;
      }
      if (this.catchAllUriParam != null
          && this.catchAllUriParam.removeRequestHandlerMatcherPair(requestHandlerMatcherPair)) {
        if (this.catchAllUriParam.isEmpty()) {
          this.catchAllUriParam = null;
        }
        removeSelfIfEmpty();
        return true;
      }
      return false;
    }

    /**
     * Notifies its parent to remove this path if empty.
     */
    private void removeSelfIfEmpty() {
      if (this.isEmpty() && parent != null) {
        parent.removeChildPath(name);
      }
    }
  }

  /**
   * Association of a {@link RequestHandler} and a {@link PathAndMethodRequestMatcher} as they were introduced, which allows a
   * joint view and availability handling.
   */
  public class RequestHandlerMatcherPair {

    private PathAndMethodRequestMatcher requestMatcher;
    private RequestHandler requestHandler;
    private boolean running = true;

    private RequestHandlerMatcherPair(PathAndMethodRequestMatcher requestMatcher, RequestHandler requestHandler) {
      this.requestMatcher = requestMatcher;
      this.requestHandler = requestHandler;
    }

    public PathAndMethodRequestMatcher getRequestMatcher() {
      return requestMatcher;
    }

    public RequestHandler getRequestHandler() {
      return requestHandler;
    }

    public boolean isRunning() {
      return this.running;
    }

    public void setIsRunning(Boolean running) {
      this.running = running;
    }

  }
}
