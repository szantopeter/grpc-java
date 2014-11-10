package com.google.net.stubby;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/** Utility class for {@link ServerInterceptor}s. */
public class ServerInterceptors {
  // Prevent instantiation
  private ServerInterceptors() {}

  /**
   * Create a new {@code ServerServiceDefinition} whose {@link ServerCallHandler}s will call {@code
   * interceptors} before calling the pre-existing {@code ServerCallHandler}.
   */
  public static ServerServiceDefinition intercept(ServerServiceDefinition serviceDef,
                                                  ServerInterceptor... interceptors) {
    return intercept(serviceDef, Arrays.asList(interceptors));
  }

  /**
   * Create a new {@code ServerServiceDefinition} whose {@link ServerCallHandler}s will call {@code
   * interceptors} before calling the pre-existing {@code ServerCallHandler}.
   */
  public static ServerServiceDefinition intercept(ServerServiceDefinition serviceDef,
      List<ServerInterceptor> interceptors) {
    Preconditions.checkNotNull(serviceDef);
    List<ServerInterceptor> immutableInterceptors = ImmutableList.copyOf(interceptors);
    if (immutableInterceptors.isEmpty()) {
      return serviceDef;
    }
    ServerServiceDefinition.Builder serviceDefBuilder
        = ServerServiceDefinition.builder(serviceDef.getName());
    for (ServerMethodDefinition<?, ?> method : serviceDef.getMethods()) {
      wrapAndAddMethod(serviceDefBuilder, method, immutableInterceptors);
    }
    return serviceDefBuilder.build();
  }

  private static <ReqT, RespT> void wrapAndAddMethod(
      ServerServiceDefinition.Builder serviceDefBuilder, ServerMethodDefinition<ReqT, RespT> method,
      List<ServerInterceptor> interceptors) {
    ServerCallHandler<ReqT, RespT> callHandler
        = InterceptCallHandler.create(interceptors, method.getServerCallHandler());
    serviceDefBuilder.addMethod(method.withServerCallHandler(callHandler));
  }

  private static class InterceptCallHandler<ReqT, RespT> implements ServerCallHandler<ReqT, RespT> {
    public static <ReqT, RespT> InterceptCallHandler<ReqT, RespT> create(
        List<ServerInterceptor> interceptors, ServerCallHandler<ReqT, RespT> callHandler) {
      return new InterceptCallHandler<ReqT, RespT>(interceptors, callHandler);
    }

    private final List<ServerInterceptor> interceptors;
    private final ServerCallHandler<ReqT, RespT> callHandler;

    private InterceptCallHandler(List<ServerInterceptor> interceptors,
        ServerCallHandler<ReqT, RespT> callHandler) {
      this.interceptors = interceptors;
      this.callHandler = callHandler;
    }

    @Override
    public ServerCall.Listener<ReqT> startCall(String method, ServerCall<RespT> call,
        Metadata.Headers headers) {
      return ProcessInterceptorsCallHandler.create(interceptors.iterator(), callHandler)
          .startCall(method, call, headers);
    }
  }

  private static class ProcessInterceptorsCallHandler<ReqT, RespT>
      implements ServerCallHandler<ReqT, RespT> {
    public static <ReqT, RespT> ProcessInterceptorsCallHandler<ReqT, RespT> create(
        Iterator<ServerInterceptor> interceptors, ServerCallHandler<ReqT, RespT> callHandler) {
      return new ProcessInterceptorsCallHandler<ReqT, RespT>(interceptors, callHandler);
    }

    private Iterator<ServerInterceptor> interceptors;
    private final ServerCallHandler<ReqT, RespT> callHandler;

    private ProcessInterceptorsCallHandler(Iterator<ServerInterceptor> interceptors,
        ServerCallHandler<ReqT, RespT> callHandler) {
      this.interceptors = interceptors;
      this.callHandler = callHandler;
    }

    @Override
    public ServerCall.Listener<ReqT> startCall(String method, ServerCall<RespT> call,
        Metadata.Headers headers) {
      if (interceptors != null && interceptors.hasNext()) {
        return interceptors.next().interceptCall(method, call, headers, this);
      } else {
        interceptors = null;
        return callHandler.startCall(method, call, headers);
      }
    }
  }

  /**
   * Utility base class for decorating {@link ServerCall} instances.
   */
  public static class ForwardingServerCall<RespT> extends ServerCall<RespT> {

    private final ServerCall<RespT> delegate;

    public ForwardingServerCall(ServerCall<RespT> delegate) {
      this.delegate = delegate;
    }

    @Override
    public void sendHeaders(Metadata.Headers headers) {
      delegate.sendHeaders(headers);
    }

    @Override
    public void sendPayload(RespT payload) {
      delegate.sendPayload(payload);
    }

    @Override
    public void close(Status status, Metadata.Trailers trailers) {
      delegate.close(status, trailers);
    }

    @Override
    public boolean isCancelled() {
      return delegate.isCancelled();
    }
  }
}