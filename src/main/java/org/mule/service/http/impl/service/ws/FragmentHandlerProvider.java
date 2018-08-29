package org.mule.service.http.impl.service.ws;

import org.mule.runtime.http.api.ws.FragmentHandler;

import java.util.function.Consumer;

public interface FragmentHandlerProvider {

  FragmentHandler getFragmentHandler(Consumer<FragmentHandler> newFragmentHandlerCallback);
}
