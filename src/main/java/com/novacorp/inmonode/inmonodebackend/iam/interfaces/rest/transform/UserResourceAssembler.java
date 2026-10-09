package com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.interfaces.rest.resources.UserResource;

public final class UserResourceAssembler {

    private UserResourceAssembler() {}

    public static UserResource toResourceFromEntity(User user) {
        return new UserResource(user.getId(), user.getEmail(), user.getRole().name(), user.getStatus().name());
    }
}
