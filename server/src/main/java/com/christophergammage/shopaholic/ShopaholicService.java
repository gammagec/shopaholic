package com.christophergammage.shopaholic;

import com.christophergammage.ShopaholicProto;
import com.christophergammage.ShopaholicServiceGrpc;
import io.grpc.stub.StreamObserver;

public class ShopaholicService extends ShopaholicServiceGrpc.ShopaholicServiceImplBase {

    @Override
    public void updateScans(ShopaholicProto.UpdateScansRequest request,
                            StreamObserver<ShopaholicProto.UpdateScansResponse> responseObserver) {
        System.out.println("Got Update Scans Request");
        System.out.println("Position: " + request.getPosition());
        System.out.println("Range: " + request.getRange());
        System.out.println("Warp: " + request.getWarp());
        for (ShopaholicProto.ShopChest shopChest : request.getShopChestsList()) {
            System.out.println("Shop Chest: " + shopChest);
        }
        ShopaholicProto.UpdateScansResponse response = ShopaholicProto.UpdateScansResponse
                .newBuilder()
                .setUpdatedShops(10)
                .setAddedShops(9)
                .setRemovedShops(8)
                .setSuccess(true)
                .build();
        System.out.println("Got Update Scans Request");
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
