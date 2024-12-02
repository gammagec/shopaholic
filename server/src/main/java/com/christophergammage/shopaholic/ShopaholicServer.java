package com.christophergammage.shopaholic;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;

public class ShopaholicServer {

    public static void main(String[] args) throws Exception {
        Server server = ServerBuilder.forPort(8080)
                .addService(ProtoReflectionService.newInstance())
                .addService(new ShopaholicService()).build();

        System.out.println("Server starting...");
        server.start();
        server.awaitTermination();
    }
}
