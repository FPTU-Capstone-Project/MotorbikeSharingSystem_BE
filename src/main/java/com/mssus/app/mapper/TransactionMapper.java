package com.mssus.app.mapper;

import com.mssus.app.dto.response.wallet.TransactionResponse;
import com.mssus.app.entity.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface TransactionMapper {

    @Mapping(target = "type", expression = "java(transaction.getType() != null ? transaction.getType().name() : null)")
    @Mapping(target = "direction", expression = "java(transaction.getDirection() != null ? transaction.getDirection().name() : null)")
    @Mapping(target = "actorKind", expression = "java(transaction.getActorKind() != null ? transaction.getActorKind().name() : null)")
    @Mapping(target = "actorUserId", source = "actorUser.userId")
    @Mapping(target = "actorUsername", source = "actorUser.fullName")
    @Mapping(target = "systemWallet", expression = "java(transaction.getSystemWallet() != null ? transaction.getSystemWallet().name() : null)")
    @Mapping(target = "status", expression = "java(transaction.getStatus() != null ? transaction.getStatus().name() : null)")
    @Mapping(target = "sharedRideId", source = "sharedRide.sharedRideId")
    @Mapping(target = "sharedRideRequestId", source = "sharedRideRequest.sharedRideRequestId")
    TransactionResponse mapToTransactionResponse(Transaction transaction);
}
