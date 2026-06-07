package com.windowsense.integration.thingsboard;

public interface ThingsBoardRpcService {

    ThingsBoardRpcResult sendTwoWayRpc(String tbDeviceId, ThingsBoardRpcRequest request);
}
